package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.report.offline.OfflineShardDescriptor;
import io.github.dependencyanalysis.report.offline.OfflineShardWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

// Wiki: wiki/features/repository-dependency-tree-report.md - Core Flow
/** Projects one Reactor into bounded offline Tree Report shards. */
final class TreeReportDataWriter {

    /** Tree Reactor browser data schema. */
    static final int SCHEMA_VERSION = 1;

    /** Four MiB normal shard target. */
    static final int MAX_SHARD_BYTES = 4 * 1024 * 1024;

    /** Rows aligned to the smallest supported browser page. */
    private static final int PAGE_SHARD_RECORDS = 10;

    /** Index records scanned progressively per script. */
    private static final int INDEX_SHARD_RECORDS = 1000;

    /** Shared local JavaScript shard writer. */
    private final OfflineShardWriter shards = new OfflineShardWriter(
            SCHEMA_VERSION, "window.__CIA_TREE_REPORT_SHARD__",
            MAX_SHARD_BYTES);

    /** Page-aligned payload writer. */
    private final OfflineShardWriter pageShards = new OfflineShardWriter(
            SCHEMA_VERSION, "window.__CIA_TREE_REPORT_SHARD__",
            MAX_SHARD_BYTES, PAGE_SHARD_RECORDS);

    /** Progressively scanned dependency index writer. */
    private final OfflineShardWriter indexShards = new OfflineShardWriter(
            SCHEMA_VERSION, "window.__CIA_TREE_REPORT_SHARD__",
            MAX_SHARD_BYTES, INDEX_SHARD_RECORDS);

    /** One Module dependency tree per lazy shard. */
    private final OfflineShardWriter treeShards = new OfflineShardWriter(
            SCHEMA_VERSION, "window.__CIA_TREE_REPORT_SHARD__",
            MAX_SHARD_BYTES, 1);

    /**
     * Writes all Reactor browser data.
     *
     * @param reactor immutable Reactor result
     * @param directory physical data directory
     * @param relativeDirectory page-relative data directory
     * @param groupingCache optional command-owned grouping cache
     * @return manifest and stable report metrics
     * @throws IOException on projection or publication failure
     */
    TreeReportData write(
            final ReactorTreeResult reactor,
            final Path directory,
            final String relativeDirectory,
            final Path groupingCache) throws IOException {
        final Projection projection = project(reactor, groupingCache);
        final Map<String, List<OfflineShardDescriptor>> descriptors =
                new LinkedHashMap<>();
        descriptors.put("dependency-ranges", shards.write(
                "dependency-ranges", projection.ranges().size(),
                id -> rangeRecord(projection.ranges().get(id)),
                directory, relativeDirectory));
        descriptors.put("dependency-index", indexShards.write(
                "dependency-index", projection.dependencyRows().size(),
                id -> dependencyIndexRecord(
                        projection.dependencyRows().get(id)),
                directory, relativeDirectory));
        descriptors.put("dependency-rows", pageShards.write(
                "dependency-rows", projection.dependencyRows().size(),
                id -> dependencyRowRecord(
                        projection.dependencyRows().get(id)),
                directory, relativeDirectory,
                projection.moduleRowStarts()::contains));
        descriptors.put("internal-conflicts", pageShards.write(
                "internal-conflicts", projection.internalConflicts().size(),
                id -> internalConflictRecord(
                        projection.internalConflicts().get(id)),
                directory, relativeDirectory,
                projection.moduleInternalStarts()::contains));
        descriptors.put("class-conflicts", pageShards.write(
                "class-conflicts", projection.classConflicts().size(),
                id -> classConflictRecord(
                        projection.classConflicts().get(id)),
                directory, relativeDirectory,
                projection.moduleClassStarts()::contains));
        descriptors.put("class-sources", pageShards.write(
                "class-sources", projection.classSources().size(),
                id -> classSourceRecord(projection.classSources().get(id)),
                directory, relativeDirectory,
                projection.classSourceStarts()::contains));
        descriptors.put("dependency-trees", treeShards.write(
                "dependency-trees", reactor.getModules().size(),
                id -> dependencyTreeRecord(id,
                        reactor.getModules().get(id)),
                directory, relativeDirectory));
        final TreeReportManifest manifest = new TreeReportManifest(
                SCHEMA_VERSION, projection.dependencyRows().size(),
                projection.modules(), projection.dependencies(),
                projection.scopes(), Collections.unmodifiableMap(
                        new LinkedHashMap<>(descriptors)));
        return new TreeReportData(manifest,
                projection.multiVersionDependencyCount(),
                projection.moduleMultiVersionCounts(),
                shardCount(descriptors), shardBytes(descriptors));
    }

    private Projection project(
            final ReactorTreeResult reactor,
            final Path groupingCache) throws IOException {
        final List<ModuleTreeResult> modules = reactor.getModules();
        final Map<DependencyKey, Integer> dependencyIds =
                dependencyIds(modules);
        final Map<DependencyKey, Set<String>> resolvedVersions =
                resolvedVersions(modules);
        final Set<DependencyKey> multiVersion = resolvedVersions.entrySet()
                .stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        final Map<Integer, Integer> moduleMultiVersionCounts =
                moduleMultiVersionCounts(modules, multiVersion);

        final List<DependencyRow> dependencyRows = new ArrayList<>();
        final Map<Integer, List<Integer>> rowsByDependency = new TreeMap<>();
        final Map<String, List<Integer>> rowsByScope = new TreeMap<>();
        final List<ModuleDraft> moduleDrafts = new ArrayList<>();
        final List<InternalConflict> internalConflicts = new ArrayList<>();
        final List<ClassConflict> classConflicts = new ArrayList<>();
        final List<ClassSource> classSources = new ArrayList<>();
        final Set<Integer> moduleRowStarts = new LinkedHashSet<>();
        final Set<Integer> moduleInternalStarts = new LinkedHashSet<>();
        final Set<Integer> moduleClassStarts = new LinkedHashSet<>();
        final Set<Integer> classSourceStarts = new LinkedHashSet<>();

        for (int moduleId = 0; moduleId < modules.size(); moduleId++) {
            final ModuleTreeResult module = modules.get(moduleId);
            final int dependencyStart = dependencyRows.size();
            moduleRowStarts.add(dependencyStart);
            for (DependencyOccurrence occurrence : module.getOccurrences()) {
                final int rowId = dependencyRows.size();
                final int dependencyId = dependencyIds.get(
                        occurrence.getKey());
                final String dependency = occurrence.getKey().toString();
                final String chain = String.join(" → ",
                        occurrence.getPath());
                final String scope = occurrence.getEffectiveScope();
                final String original = occurrence.getRequestedVersion();
                final String resolved = occurrence.getSelectedVersion();
                final String search = String.join(" ", dependency, scope,
                        module.getCoordinate(), chain, original, resolved)
                        .toLowerCase(Locale.ROOT);
                dependencyRows.add(new DependencyRow(rowId, dependencyId,
                        moduleId, scope, chain, original, resolved, search));
                rowsByDependency.computeIfAbsent(dependencyId,
                        ignored -> new ArrayList<>()).add(rowId);
                rowsByScope.computeIfAbsent(scope,
                        ignored -> new ArrayList<>()).add(rowId);
            }
            final int internalStart = internalConflicts.size();
            moduleInternalStarts.add(internalStart);
            for (VersionMediationIssue issue : internalIssues(
                    module, groupingCache)) {
                internalConflicts.add(internalConflict(
                        internalConflicts.size(), moduleId, issue));
            }
            final int classStart = classConflicts.size();
            moduleClassStarts.add(classStart);
            appendClassConflicts(module, moduleId,
                    classConflicts, classSources, classSourceStarts);
            moduleDrafts.add(new ModuleDraft(moduleId,
                    module.getCoordinate().isBlank()
                            ? module.getPom().toString()
                            : module.getCoordinate(),
                    module.getCoordinate(), module.getPom().toString(),
                    module.getFailure(), dependencyStart,
                    dependencyRows.size() - dependencyStart,
                    internalStart, internalConflicts.size() - internalStart,
                    classStart, classConflicts.size() - classStart,
                    moduleMultiVersionCounts.getOrDefault(moduleId, 0)));
        }

        final List<Range> ranges = new ArrayList<>();
        final List<TreeReportManifest.DependencyDescriptor> dependencies =
                new ArrayList<>();
        dependencyIds.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    final int first = ranges.size();
                    ranges.addAll(ranges(rowsByDependency.getOrDefault(
                            entry.getValue(), List.of()), ranges.size()));
                    dependencies.add(new TreeReportManifest
                            .DependencyDescriptor(entry.getValue(),
                            entry.getKey().toString(),
                            resolvedVersions.getOrDefault(
                                    entry.getKey(), Set.of()).size(),
                            first, ranges.size() - first));
                });
        final List<TreeReportManifest.ScopeDescriptor> scopes =
                new ArrayList<>();
        rowsByScope.forEach((scope, ids) -> {
            final int first = ranges.size();
            ranges.addAll(ranges(ids, ranges.size()));
            scopes.add(new TreeReportManifest.ScopeDescriptor(
                    scope, first, ranges.size() - first));
        });
        final List<TreeReportManifest.ModuleDescriptor> descriptors =
                moduleDrafts.stream().map(ModuleDraft::descriptor).toList();
        return new Projection(List.copyOf(dependencyRows),
                List.copyOf(ranges), List.copyOf(dependencies),
                List.copyOf(scopes), descriptors,
                List.copyOf(internalConflicts),
                List.copyOf(classConflicts), List.copyOf(classSources),
                Set.copyOf(moduleRowStarts),
                Set.copyOf(moduleInternalStarts),
                Set.copyOf(moduleClassStarts),
                Set.copyOf(classSourceStarts), multiVersion.size(),
                Map.copyOf(moduleMultiVersionCounts));
    }

    private Map<DependencyKey, Integer> dependencyIds(
            final List<ModuleTreeResult> modules) {
        final Set<DependencyKey> keys = new TreeSet<>();
        modules.forEach(module -> module.getOccurrences().stream()
                .map(DependencyOccurrence::getKey).forEach(keys::add));
        final Map<DependencyKey, Integer> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, result.size()));
        return result;
    }

    private Map<DependencyKey, Set<String>> resolvedVersions(
            final List<ModuleTreeResult> modules) {
        final Map<DependencyKey, Set<String>> result = new TreeMap<>();
        for (ModuleTreeResult module : modules) {
            for (DependencyOccurrence occurrence : module.getOccurrences()) {
                if (occurrence.isSelected()
                        && !occurrence.getSelectedVersion().isBlank()) {
                    result.computeIfAbsent(occurrence.getKey(),
                            ignored -> new TreeSet<>())
                            .add(occurrence.getSelectedVersion());
                }
            }
        }
        return result;
    }

    private Map<Integer, Integer> moduleMultiVersionCounts(
            final List<ModuleTreeResult> modules,
            final Set<DependencyKey> multiVersion) {
        final Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int moduleId = 0; moduleId < modules.size(); moduleId++) {
            final Set<DependencyKey> keys = modules.get(moduleId)
                    .getOccurrences().stream()
                    .filter(DependencyOccurrence::isSelected)
                    .map(DependencyOccurrence::getKey)
                    .filter(multiVersion::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.put(moduleId, keys.size());
        }
        return result;
    }

    private List<VersionMediationIssue> internalIssues(
            final ModuleTreeResult module,
            final Path groupingCache) throws IOException {
        return groupingCache == null
                ? new ModuleVersionAnalyzer().analyze(module)
                : new ModuleVersionAnalyzer().analyze(module, groupingCache);
    }

    private InternalConflict internalConflict(
            final int id,
            final int moduleId,
            final VersionMediationIssue issue) {
        final List<Evidence> evidence = new ArrayList<>();
        final Set<String> scopes = new TreeSet<>();
        for (VersionPath path : issue.getPaths()) {
            scopes.add(path.getScope());
            for (VersionEvidence source : path.getEvidence()) {
                evidence.add(new Evidence(source.getSource().name(),
                        source.getSource()
                                == VersionEvidenceSource.DEPENDENCY_MANAGEMENT
                                ? "" : String.join(" → ", path.getPath()),
                        source.getVersion(), path.getScope()));
            }
        }
        final String scope = String.join(", ", scopes);
        final String search = (issue.getKey() + " " + scope + " "
                + issue.getSelectedVersion() + " " + evidence.stream()
                .map(Evidence::searchText).collect(Collectors.joining(" ")))
                .toLowerCase(Locale.ROOT);
        return new InternalConflict(id, moduleId, issue.getKey().toString(),
                scope, issue.getSelectedVersion(), List.copyOf(evidence),
                search);
    }

    private void appendClassConflicts(
            final ModuleTreeResult module,
            final int moduleId,
            final List<ClassConflict> conflicts,
            final List<ClassSource> sources,
            final Set<Integer> sourceStarts) {
        final List<TreeClassConflict> sorted = module.getClassConflicts()
                .stream().sorted(Comparator
                        .comparing((TreeClassConflict value) ->
                                value.risk() == ClassConflictRisk.HIGH ? 0 : 1)
                        .thenComparing(TreeClassConflict::binaryName))
                .toList();
        for (TreeClassConflict conflict : sorted) {
            sourceStarts.add(sources.size());
            final List<Integer> sourceIds = new ArrayList<>();
            for (TreeClassConflictCandidate candidate
                    : conflict.candidates()) {
                final int sourceId = sources.size();
                sourceIds.add(sourceId);
                sources.add(new ClassSource(sourceId,
                        sourceLabel(candidate),
                        candidate == conflict.winner(),
                        candidate.decompiled().isAvailable(),
                        candidate.decompiled().isAvailable()
                                ? candidate.decompiled().getSource() : ""));
            }
            final String winner = sourceLabel(conflict.winner());
            final String shadowed = conflict.shadowed().stream()
                    .map(this::sourceLabel)
                    .collect(Collectors.joining(" | "));
            final String search = String.join(" ", conflict.displayName(),
                    conflict.risk().name(), winner, shadowed,
                    conflict.selection()).toLowerCase(Locale.ROOT);
            conflicts.add(new ClassConflict(conflicts.size(), moduleId,
                    conflict.displayName(), conflict.risk().name(),
                    conflict.risk() == ClassConflictRisk.HIGH ? 0 : 1,
                    winner, shadowed, conflict.selection(),
                    List.copyOf(sourceIds), search));
        }
    }

    private String sourceLabel(final TreeClassConflictCandidate candidate) {
        return candidate.origin() + " — " + candidate.source()
                + (candidate.scope().isBlank()
                ? "" : " [" + candidate.scope() + "]");
    }

    private List<Range> ranges(
            final List<Integer> ids,
            final int firstId) {
        final List<Range> result = new ArrayList<>();
        int start = -1;
        int previous = -1;
        for (int rowId : ids) {
            if (start < 0) {
                start = rowId;
            } else if (rowId != previous + 1) {
                result.add(new Range(firstId + result.size(), start,
                        previous - start + 1));
                start = rowId;
            }
            previous = rowId;
        }
        if (start >= 0) {
            result.add(new Range(firstId + result.size(), start,
                    previous - start + 1));
        }
        return result;
    }

    private OfflineShardWriter.JsonRecord rangeRecord(final Range value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("rowStart", value.start());
            json.writeNumberField("rowCount", value.count());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord dependencyIndexRecord(
            final DependencyRow value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("dependencyId", value.dependencyId());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeStringField("scope", value.scope());
            json.writeStringField("chain", value.chain());
            json.writeStringField("originalVersion", value.original());
            json.writeStringField("resolvedVersion", value.resolved());
            json.writeStringField("searchText", value.search());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord dependencyRowRecord(
            final DependencyRow value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("dependencyId", value.dependencyId());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeStringField("scope", value.scope());
            json.writeStringField("chain", value.chain());
            json.writeStringField("originalVersion", value.original());
            json.writeStringField("resolvedVersion", value.resolved());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord internalConflictRecord(
            final InternalConflict value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeStringField("dependency", value.dependency());
            json.writeStringField("scope", value.scope());
            json.writeStringField("resolvedVersion", value.resolved());
            json.writeStringField("searchText", value.search());
            json.writeArrayFieldStart("evidence");
            for (Evidence evidence : value.evidence()) {
                json.writeStartObject();
                json.writeStringField("source", evidence.source());
                json.writeStringField("chain", evidence.chain());
                json.writeStringField("originalVersion",
                        evidence.original());
                json.writeStringField("scope", evidence.scope());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord classConflictRecord(
            final ClassConflict value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeStringField("className", value.className());
            json.writeStringField("risk", value.risk());
            json.writeNumberField("riskOrder", value.riskOrder());
            json.writeStringField("winner", value.winner());
            json.writeStringField("shadowed", value.shadowed());
            json.writeStringField("selection", value.selection());
            json.writeStringField("searchText", value.search());
            json.writeArrayFieldStart("sourceIds");
            for (int sourceId : value.sourceIds()) {
                json.writeNumber(sourceId);
            }
            json.writeEndArray();
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord classSourceRecord(
            final ClassSource value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeStringField("label", value.label());
            json.writeBooleanField("winner", value.winner());
            json.writeBooleanField("available", value.available());
            if (value.available()) {
                json.writeStringField("sourceCode", value.sourceCode());
            }
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord dependencyTreeRecord(
            final int id,
            final ModuleTreeResult module) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", id);
            json.writeNumberField("moduleId", id);
            json.writeStringField("failure", module.getFailure());
            json.writeStringField("text", dependencyTree(module));
            json.writeEndObject();
        };
    }

    private String dependencyTree(final ModuleTreeResult module) {
        final StringBuilder result = new StringBuilder();
        final List<DependencyOccurrence> occurrences =
                module.getOccurrences();
        String rootCoordinate = module.getCoordinate();
        for (DependencyOccurrence occurrence : occurrences) {
            if (!occurrence.getPath().isEmpty()) {
                rootCoordinate = occurrence.getPath().get(0);
                break;
            }
        }
        List<String> previousPath = List.of();
        for (int occurrenceIndex = 0;
             occurrenceIndex < occurrences.size(); occurrenceIndex++) {
            final DependencyOccurrence occurrence = occurrences.get(
                    occurrenceIndex);
            final List<String> path = occurrencePath(
                    occurrence, rootCoordinate);
            final int common = commonPrefixLength(previousPath, path);
            final int start = common == path.size()
                    ? Math.max(0, path.size() - 1) : common;
            for (int index = start; index < path.size(); index++) {
                final boolean terminal = index == path.size() - 1;
                appendMavenLine(result, path.subList(0, index + 1),
                        terminal ? occurrence.coordinate() : path.get(index),
                        terminal ? occurrence : null, occurrences,
                        occurrenceIndex, rootCoordinate);
            }
            previousPath = path;
        }
        return result.toString();
    }

    private List<String> occurrencePath(
            final DependencyOccurrence occurrence,
            final String rootCoordinate) {
        return occurrence.getPath().isEmpty()
                ? List.of(rootCoordinate, occurrence.coordinate())
                : occurrence.getPath();
    }

    private int commonPrefixLength(
            final List<String> left,
            final List<String> right) {
        final int maximum = Math.min(left.size(), right.size());
        int common = 0;
        while (common < maximum && left.get(common)
                .equals(right.get(common))) {
            common++;
        }
        return common;
    }

    private void appendMavenLine(
            final StringBuilder output,
            final List<String> path,
            final String coordinate,
            final DependencyOccurrence occurrence,
            final List<DependencyOccurrence> occurrences,
            final int occurrenceIndex,
            final String rootCoordinate) {
        final int depth = path.size() - 1;
        for (int ancestor = 1; ancestor < depth; ancestor++) {
            output.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, ancestor, rootCoordinate, false)
                    ? "|  " : "   ");
        }
        if (depth > 0) {
            output.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, depth, rootCoordinate,
                    occurrence != null) ? "+- " : "\\- ");
        }
        output.append(coordinate);
        if (occurrence != null) {
            output.append(verboseAnnotation(occurrence));
        }
        output.append('\n');
    }

    private boolean hasLaterSibling(
            final List<String> current,
            final List<DependencyOccurrence> occurrences,
            final int currentIndex,
            final int depth,
            final String rootCoordinate,
            final boolean terminal) {
        for (int index = currentIndex + 1;
             index < occurrences.size(); index++) {
            final List<String> candidate = occurrencePath(
                    occurrences.get(index), rootCoordinate);
            if (candidate.size() <= depth
                    || !current.subList(0, depth).equals(
                    candidate.subList(0, depth))) {
                return false;
            }
            if (current.get(depth).equals(candidate.get(depth))) {
                if (terminal && candidate.size() == current.size()) {
                    return true;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private String verboseAnnotation(
            final DependencyOccurrence occurrence) {
        final List<String> values = new ArrayList<>();
        if (!occurrence.getManagedFromVersion().isBlank()) {
            values.add("version managed from "
                    + occurrence.getManagedFromVersion());
        }
        if (!occurrence.getManagedFromScope().isBlank()) {
            values.add("scope managed from "
                    + occurrence.getManagedFromScope());
        }
        if (Boolean.TRUE.equals(occurrence.getOptional())) {
            values.add("optional");
        }
        if (!occurrence.isSelected()) {
            values.add(omittedAnnotation(occurrence));
        }
        if (occurrence.isReactorModule()) {
            values.add("reactor module");
        }
        return values.isEmpty() ? ""
                : " (" + String.join("; ", values) + ")";
    }

    private String omittedAnnotation(
            final DependencyOccurrence occurrence) {
        final String reason = occurrence.getOmittedReason();
        if (reason.equals("conflict")) {
            return "omitted for conflict with "
                    + occurrence.getSelectedVersion();
        }
        if (reason.startsWith("omitted ")) {
            return reason;
        }
        return reason.isBlank() ? "omitted" : "omitted for " + reason;
    }

    private int shardCount(
            final Map<String, List<OfflineShardDescriptor>> descriptors) {
        return descriptors.values().stream().mapToInt(List::size).sum();
    }

    private long shardBytes(
            final Map<String, List<OfflineShardDescriptor>> descriptors) {
        return descriptors.values().stream().flatMap(List::stream)
                .mapToLong(OfflineShardDescriptor::bytes).sum();
    }

    /**
     * Published data and summary metrics.
     *
     * @param manifest lightweight browser manifest
     * @param multiVersionDependencyCount Reactor multi-version count
     * @param moduleMultiVersionCounts Module counts by stable Module ID
     * @param shardCount total emitted shard count
     * @param shardBytes total emitted shard bytes
     */
    record TreeReportData(
            TreeReportManifest manifest,
            int multiVersionDependencyCount,
            Map<Integer, Integer> moduleMultiVersionCounts,
            int shardCount,
            long shardBytes) {
    }

    private record Projection(
            List<DependencyRow> dependencyRows,
            List<Range> ranges,
            List<TreeReportManifest.DependencyDescriptor> dependencies,
            List<TreeReportManifest.ScopeDescriptor> scopes,
            List<TreeReportManifest.ModuleDescriptor> modules,
            List<InternalConflict> internalConflicts,
            List<ClassConflict> classConflicts,
            List<ClassSource> classSources,
            Set<Integer> moduleRowStarts,
            Set<Integer> moduleInternalStarts,
            Set<Integer> moduleClassStarts,
            Set<Integer> classSourceStarts,
            int multiVersionDependencyCount,
            Map<Integer, Integer> moduleMultiVersionCounts) {
    }

    private record ModuleDraft(
            int id,
            String label,
            String coordinate,
            String pom,
            String failure,
            int dependencyStart,
            int dependencyCount,
            int internalStart,
            int internalCount,
            int classStart,
            int classCount,
            int multiVersionDependencies) {
        TreeReportManifest.ModuleDescriptor descriptor() {
            return new TreeReportManifest.ModuleDescriptor(id, label,
                    coordinate, pom, failure, dependencyStart,
                    dependencyCount, internalStart, internalCount,
                    classStart, classCount, id,
                    multiVersionDependencies);
        }
    }

    private record DependencyRow(
            int id,
            int dependencyId,
            int moduleId,
            String scope,
            String chain,
            String original,
            String resolved,
            String search) {
    }

    private record Range(int id, int start, int count) {
    }

    private record InternalConflict(
            int id,
            int moduleId,
            String dependency,
            String scope,
            String resolved,
            List<Evidence> evidence,
            String search) {
    }

    private record Evidence(
            String source,
            String chain,
            String original,
            String scope) {
        String searchText() {
            return String.join(" ", source, chain, original, scope);
        }
    }

    private record ClassConflict(
            int id,
            int moduleId,
            String className,
            String risk,
            int riskOrder,
            String winner,
            String shadowed,
            String selection,
            List<Integer> sourceIds,
            String search) {
    }

    private record ClassSource(
            int id,
            String label,
            boolean winner,
            boolean available,
            String sourceCode) {
    }
}
