package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.report.offline.OfflineShardDescriptor;
import io.github.dependencyanalysis.report.offline.OfflineShardWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// Wiki: wiki/features/repository-dependency-tree-diff.md - Schema v1数据投影
/** Projects one Reactor diff into bounded offline Schema v1 shards. */
final class TreeDiffReportDataWriter {

    /** Four MiB normal shard target. */
    static final int MAX_SHARD_BYTES = 4 * 1024 * 1024;

    /** Shared schema-named shard writer. */
    private final OfflineShardWriter shards = new OfflineShardWriter(
            TreeDiffReportManifest.SCHEMA,
            TreeDiffReportManifest.VERSION,
            "window.__CIA_TREE_DIFF_REPORT_SHARD__",
            MAX_SHARD_BYTES);

    /** Bounded visible-row shard writer. */
    private final OfflineShardWriter pageShards = new OfflineShardWriter(
            TreeDiffReportManifest.SCHEMA,
            TreeDiffReportManifest.VERSION,
            "window.__CIA_TREE_DIFF_REPORT_SHARD__",
            MAX_SHARD_BYTES, 100);

    /** One tree pair per shard. */
    private final OfflineShardWriter treeShards = new OfflineShardWriter(
            TreeDiffReportManifest.SCHEMA,
            TreeDiffReportManifest.VERSION,
            "window.__CIA_TREE_DIFF_REPORT_SHARD__",
            MAX_SHARD_BYTES, 1);

    /**
     * Writes browser data for one complete Reactor page.
     *
     * @param reactor Reactor diff
     * @param metadata command metadata
     * @param directory physical data directory
     * @param relativeDirectory page-relative data directory
     * @return manifest and publication metrics
     * @throws IOException on write failure
     */
    TreeDiffReportData write(
            final TreeDiffReactorResult reactor,
            final TreeDiffReportMetadata metadata,
            final Path directory,
            final String relativeDirectory) throws IOException {
        final Projection projection = project(reactor);
        final Map<String, List<OfflineShardDescriptor>> descriptors =
                new LinkedHashMap<>();
        descriptors.put("module-summaries", pageShards.write(
                "module-summaries", projection.summaries().size(),
                id -> summaryRecord(projection.summaries().get(id)),
                directory, relativeDirectory));
        descriptors.put("module-dependencies", shards.write(
                "module-dependencies",
                projection.moduleDependencies().size(),
                id -> moduleDependencyRecord(
                        projection.moduleDependencies().get(id)),
                directory, relativeDirectory,
                projection.moduleDependencyStarts()::contains));
        descriptors.put("dependencies", shards.write(
                "dependencies", projection.dependencies().size(),
                id -> dependencyRecord(projection.dependencies().get(id)),
                directory, relativeDirectory));
        descriptors.put("rows", pageShards.write(
                "rows", projection.rows().size(),
                id -> rowRecord(projection.rows().get(id)),
                directory, relativeDirectory,
                projection.moduleRowStarts()::contains));
        descriptors.put("chain-rows", pageShards.write(
                "chain-rows", projection.chains().size(),
                id -> chainRecord(projection.chains().get(id)),
                directory, relativeDirectory,
                projection.chainStarts()::contains));
        descriptors.put("trees", treeShards.write(
                "trees", projection.trees().size(),
                id -> treeRecord(projection.trees().get(id)),
                directory, relativeDirectory));
        final List<String> warnings = oversizedWarnings(descriptors);
        final TreeDiffReportManifest manifest =
                new TreeDiffReportManifest(reactor.reactorKey(), metadata,
                        projection.catalog(),
                        Collections.unmodifiableMap(descriptors), warnings);
        return new TreeDiffReportData(manifest, shardCount(descriptors),
                shardBytes(descriptors), warnings);
    }

    private Projection project(final TreeDiffReactorResult reactor) {
        final List<TreeDiffModuleResult> modules = reactor.modules();
        final Map<DependencyKey, Integer> dependencyIds =
                dependencyIds(modules);
        final List<DependencyCatalog> dependencies = dependencyIds.entrySet()
                .stream().sorted(Map.Entry.comparingByValue())
                .map(entry -> new DependencyCatalog(entry.getValue(),
                        entry.getKey(), display(entry.getKey())))
                .toList();
        final List<TreeDiffReportManifest.ModuleCatalogRecord> catalog =
                new ArrayList<>();
        final List<ModuleSummary> summaries = new ArrayList<>();
        final List<ModuleDependency> moduleDependencies = new ArrayList<>();
        final List<MainRow> rows = new ArrayList<>();
        final List<ChainRow> chains = new ArrayList<>();
        final List<TreePair> trees = new ArrayList<>();
        final Set<Integer> moduleDependencyStarts = new LinkedHashSet<>();
        final Set<Integer> moduleRowStarts = new LinkedHashSet<>();
        final Set<Integer> chainStarts = new LinkedHashSet<>();
        for (int moduleId = 0; moduleId < modules.size(); moduleId++) {
            final TreeDiffModuleResult module = modules.get(moduleId);
            catalog.add(new TreeDiffReportManifest.ModuleCatalogRecord(
                    moduleId, module.moduleKey(),
                    module.baselineCoordinate(), module.targetCoordinate(),
                    module.baselineState(), module.targetState(),
                    module.comparisonStatus()));
            final int rowStart = rows.size();
            final int moduleDependencyStart = moduleDependencies.size();
            moduleRowStarts.add(rowStart);
            moduleDependencyStarts.add(moduleDependencyStart);
            final Map<TreeDependencyBaseChangeType, List<Integer>> byType =
                    new EnumMap<>(TreeDependencyBaseChangeType.class);
            final List<Integer> scopeChanged = new ArrayList<>();
            for (TreeDependencyDiffRecord dependency
                    : module.dependencies()) {
                final int rowId = rows.size();
                final int dependencyId = dependencyIds.get(
                        dependency.key());
                final int chainStart = chains.size();
                chainStarts.add(chainStart);
                for (TreeDiffChainRow chain : dependency.chains()) {
                    chains.add(toChainRow(chains.size(), rowId, chain));
                }
                rows.add(toMainRow(rowId, moduleId, dependencyId,
                        dependency, chainStart,
                        chains.size() - chainStart));
                moduleDependencies.add(new ModuleDependency(
                        moduleDependencies.size(), moduleId,
                        dependencyId, rowId));
                byType.computeIfAbsent(dependency.baseChangeType(),
                        ignored -> new ArrayList<>()).add(rowId);
                if (dependency.scopeChanged()) {
                    scopeChanged.add(rowId);
                }
            }
            final Map<TreeDependencyBaseChangeType, List<Range>> ranges =
                    new EnumMap<>(TreeDependencyBaseChangeType.class);
            byType.forEach((type, ids) -> ranges.put(type, ranges(ids)));
            summaries.add(new ModuleSummary(moduleId,
                    module.baselineState(), module.targetState(),
                    module.comparisonStatus(), module.metrics(), rowStart,
                    rows.size() - rowStart, moduleDependencyStart,
                    moduleDependencies.size() - moduleDependencyStart,
                    ranges, ranges(scopeChanged), module.issues()));
            trees.add(treePair(moduleId, module));
        }
        return new Projection(catalog, summaries, dependencies,
                moduleDependencies, rows, chains, trees,
                moduleDependencyStarts, moduleRowStarts, chainStarts);
    }

    private Map<DependencyKey, Integer> dependencyIds(
            final List<TreeDiffModuleResult> modules) {
        final Set<DependencyKey> keys = new TreeSet<>();
        for (TreeDiffModuleResult module : modules) {
            for (TreeDependencyDiffRecord dependency
                    : module.dependencies()) {
                keys.add(dependency.key());
            }
        }
        final Map<DependencyKey, Integer> result = new TreeMap<>();
        for (DependencyKey key : keys) {
            result.put(key, result.size());
        }
        return result;
    }

    private MainRow toMainRow(
            final int rowId,
            final int moduleId,
            final int dependencyId,
            final TreeDependencyDiffRecord dependency,
            final int chainStart,
            final int chainCount) {
        return new MainRow(rowId, moduleId, dependencyId,
                value(dependency.baseline(), SideValue.VERSION),
                value(dependency.target(), SideValue.VERSION),
                value(dependency.baseline(), SideValue.SCOPE),
                value(dependency.target(), SideValue.SCOPE),
                dependency.baseline() == null
                        ? null : dependency.baseline().direct(),
                dependency.target() == null
                        ? null : dependency.target().direct(),
                dependency.baseChangeType(), dependency.scopeChanged(),
                chainStart, chainCount);
    }

    private String value(
            final TreeDiffSideDependency dependency,
            final SideValue value) {
        if (dependency == null) {
            return null;
        }
        return value == SideValue.VERSION
                ? dependency.resolvedVersion() : dependency.scope();
    }

    private ChainRow toChainRow(
            final int chainId,
            final int parentRowId,
            final TreeDiffChainRow chain) {
        return new ChainRow(chainId, parentRowId,
                pathValue(chain.baseline(), PathValue.DISPLAY),
                pathValue(chain.target(), PathValue.DISPLAY),
                pathValue(chain.baseline(), PathValue.VERSION),
                pathValue(chain.target(), PathValue.VERSION),
                pathValue(chain.baseline(), PathValue.MANAGED),
                pathValue(chain.target(), PathValue.MANAGED),
                chain.changeType());
    }

    private String pathValue(
            final TreeDiffPathOccurrence path,
            final PathValue value) {
        if (path == null) {
            return null;
        }
        return switch (value) {
            case DISPLAY -> path.display();
            case VERSION -> path.resolvedVersion();
            case MANAGED -> path.managedFromVersion();
        };
    }

    private TreePair treePair(
            final int moduleId,
            final TreeDiffModuleResult module) {
        final MavenTextTreeFormatter formatter =
                new MavenTextTreeFormatter();
        return new TreePair(moduleId,
                new TreeSide(module.baselineState(),
                        module.baselineModule() == null ? ""
                                : formatter.format(
                                module.baselineModule()),
                        sideIssue(module, true)),
                new TreeSide(module.targetState(),
                        module.targetModule() == null ? ""
                                : formatter.format(module.targetModule()),
                        sideIssue(module, false)));
    }

    private String sideIssue(
            final TreeDiffModuleResult module,
            final boolean baseline) {
        final ModuleTreeResult side = baseline
                ? module.baselineModule() : module.targetModule();
        if (side != null && !side.getFailure().isBlank()) {
            return side.getFailure();
        }
        return module.issues().stream()
                .filter(issue -> issue.startsWith(
                        baseline ? "baseline:" : "target:"))
                .findFirst().orElse("");
    }

    private List<Range> ranges(final List<Integer> ids) {
        final List<Range> result = new ArrayList<>();
        int start = -1;
        int previous = -1;
        for (int id : ids) {
            if (start < 0) {
                start = id;
            } else if (id != previous + 1) {
                result.add(new Range(start, previous - start + 1));
                start = id;
            }
            previous = id;
        }
        if (start >= 0) {
            result.add(new Range(start, previous - start + 1));
        }
        return List.copyOf(result);
    }

    private OfflineShardWriter.JsonRecord summaryRecord(
            final ModuleSummary value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.moduleId());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeStringField("baselineState",
                    value.baselineState().name());
            json.writeStringField("targetState",
                    value.targetState().name());
            json.writeStringField("comparisonStatus",
                    value.comparisonStatus().name());
            if (value.metrics() == null) {
                json.writeNullField("metrics");
            } else {
                writeMetrics(json, value.metrics());
            }
            json.writeNumberField("dependencyRowStart", value.rowStart());
            json.writeNumberField("dependencyRowCount", value.rowCount());
            json.writeNumberField("moduleDependencyStart",
                    value.moduleDependencyStart());
            json.writeNumberField("moduleDependencyCount",
                    value.moduleDependencyCount());
            json.writeObjectFieldStart("baseRanges");
            for (TreeDependencyBaseChangeType type
                    : TreeDependencyBaseChangeType.values()) {
                writeRanges(json, type.name(),
                        value.baseRanges().getOrDefault(type, List.of()));
            }
            json.writeEndObject();
            writeRanges(json, "scopeChangedRanges",
                    value.scopeChangedRanges());
            json.writeArrayFieldStart("issues");
            for (String issue : value.issues()) {
                json.writeString(issue);
            }
            json.writeEndArray();
            json.writeEndObject();
        };
    }

    private void writeMetrics(
            final JsonGenerator json,
            final TreeDiffMetrics metrics) throws IOException {
        json.writeObjectFieldStart("metrics");
        json.writeNumberField("versionChanged", metrics.versionChanged());
        json.writeNumberField("added", metrics.added());
        json.writeNumberField("removed", metrics.removed());
        json.writeNumberField("resolvedUnchanged",
                metrics.resolvedUnchanged());
        json.writeNumberField("scopeChanged", metrics.scopeChanged());
        json.writeEndObject();
    }

    private void writeRanges(
            final JsonGenerator json,
            final String field,
            final List<Range> ranges) throws IOException {
        json.writeArrayFieldStart(field);
        for (Range range : ranges) {
            json.writeStartObject();
            json.writeNumberField("start", range.start());
            json.writeNumberField("count", range.count());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private OfflineShardWriter.JsonRecord moduleDependencyRecord(
            final ModuleDependency value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeNumberField("dependencyId", value.dependencyId());
            json.writeNumberField("rowId", value.rowId());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord dependencyRecord(
            final DependencyCatalog value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("dependencyId", value.id());
            json.writeStringField("groupId", value.key().getGroupId());
            json.writeStringField("artifactId", value.key().getArtifactId());
            json.writeStringField("type", value.key().getType());
            json.writeStringField("classifier", value.key().getClassifier());
            json.writeStringField("display", value.display());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord rowRecord(final MainRow value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("rowId", value.id());
            json.writeNumberField("moduleId", value.moduleId());
            json.writeNumberField("dependencyId", value.dependencyId());
            writeNullable(json, "baselineResolvedVersion",
                    value.baselineVersion());
            writeNullable(json, "targetResolvedVersion",
                    value.targetVersion());
            writeNullable(json, "baselineScope", value.baselineScope());
            writeNullable(json, "targetScope", value.targetScope());
            writeNullable(json, "baselineDirect", value.baselineDirect());
            writeNullable(json, "targetDirect", value.targetDirect());
            json.writeStringField("baseChangeType",
                    value.baseChangeType().name());
            json.writeBooleanField("scopeChanged", value.scopeChanged());
            json.writeNumberField("chainStart", value.chainStart());
            json.writeNumberField("chainCount", value.chainCount());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord chainRecord(final ChainRow value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("chainRowId", value.id());
            json.writeNumberField("parentRowId", value.parentRowId());
            writeNullable(json, "baselinePathDisplay",
                    value.baselinePath());
            writeNullable(json, "targetPathDisplay", value.targetPath());
            writeNullable(json, "baselineResolvedVersion",
                    value.baselineVersion());
            writeNullable(json, "targetResolvedVersion",
                    value.targetVersion());
            writeNullable(json, "baselineManagedFromVersion",
                    value.baselineManaged());
            writeNullable(json, "targetManagedFromVersion",
                    value.targetManaged());
            json.writeStringField("chainChangeType",
                    value.changeType().name());
            json.writeEndObject();
        };
    }

    private OfflineShardWriter.JsonRecord treeRecord(final TreePair value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.moduleId());
            json.writeNumberField("moduleId", value.moduleId());
            writeTreeSide(json, "baseline", value.baseline());
            writeTreeSide(json, "target", value.target());
            json.writeEndObject();
        };
    }

    private void writeTreeSide(
            final JsonGenerator json,
            final String field,
            final TreeSide side) throws IOException {
        json.writeObjectFieldStart(field);
        json.writeStringField("state", side.state().name());
        json.writeStringField("text", side.text());
        json.writeStringField("issue", side.issue());
        json.writeEndObject();
    }

    private void writeNullable(
            final JsonGenerator json,
            final String field,
            final String value) throws IOException {
        if (value == null) {
            json.writeNullField(field);
        } else {
            json.writeStringField(field, value);
        }
    }

    private void writeNullable(
            final JsonGenerator json,
            final String field,
            final Boolean value) throws IOException {
        if (value == null) {
            json.writeNullField(field);
        } else {
            json.writeBooleanField(field, value);
        }
    }

    private String display(final DependencyKey key) {
        return key.getGroupId() + ":" + key.getArtifactId() + ":"
                + key.getType() + (key.getClassifier().isBlank()
                ? "" : ":" + key.getClassifier());
    }

    private List<String> oversizedWarnings(
            final Map<String, List<OfflineShardDescriptor>> descriptors) {
        final List<String> result = new ArrayList<>();
        descriptors.forEach((kind, values) -> values.stream()
                .filter(value -> value.bytes() > MAX_SHARD_BYTES)
                .forEach(value -> result.add("Oversized shard: kind="
                        + kind + "; id=" + value.id() + "; bytes="
                        + value.bytes())));
        return List.copyOf(result);
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

    record TreeDiffReportData(
            TreeDiffReportManifest manifest,
            int shardCount,
            long shardBytes,
            List<String> technicalWarnings) {
    }

    private record Projection(
            List<TreeDiffReportManifest.ModuleCatalogRecord> catalog,
            List<ModuleSummary> summaries,
            List<DependencyCatalog> dependencies,
            List<ModuleDependency> moduleDependencies,
            List<MainRow> rows,
            List<ChainRow> chains,
            List<TreePair> trees,
            Set<Integer> moduleDependencyStarts,
            Set<Integer> moduleRowStarts,
            Set<Integer> chainStarts) {
    }

    private record ModuleSummary(
            int moduleId,
            TreeDiffSideState baselineState,
            TreeDiffSideState targetState,
            TreeDiffComparisonStatus comparisonStatus,
            TreeDiffMetrics metrics,
            int rowStart,
            int rowCount,
            int moduleDependencyStart,
            int moduleDependencyCount,
            Map<TreeDependencyBaseChangeType, List<Range>> baseRanges,
            List<Range> scopeChangedRanges,
            List<String> issues) {
    }

    private record DependencyCatalog(
            int id,
            DependencyKey key,
            String display) {
    }

    private record ModuleDependency(
            int id,
            int moduleId,
            int dependencyId,
            int rowId) {
    }

    private record MainRow(
            int id,
            int moduleId,
            int dependencyId,
            String baselineVersion,
            String targetVersion,
            String baselineScope,
            String targetScope,
            Boolean baselineDirect,
            Boolean targetDirect,
            TreeDependencyBaseChangeType baseChangeType,
            boolean scopeChanged,
            int chainStart,
            int chainCount) {
    }

    private record ChainRow(
            int id,
            int parentRowId,
            String baselinePath,
            String targetPath,
            String baselineVersion,
            String targetVersion,
            String baselineManaged,
            String targetManaged,
            TreeChainChangeType changeType) {
    }

    private record Range(int start, int count) {
    }

    private record TreePair(
            int moduleId,
            TreeSide baseline,
            TreeSide target) {
    }

    private record TreeSide(
            TreeDiffSideState state,
            String text,
            String issue) {
    }

    private enum SideValue {
        /** Resolved version. */
        VERSION,
        /** Effective scope. */
        SCOPE
    }

    private enum PathValue {
        /** Complete dependency chain. */
        DISPLAY,
        /** Resolved version. */
        VERSION,
        /** Managed-from version. */
        MANAGED
    }
}
