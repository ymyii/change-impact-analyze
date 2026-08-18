package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.CodeComparisonEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonStatus;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactPathRootKind;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.StructuralReferencePath;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

// Wiki: wiki/features/report-generator.md - Offline Affected Paths shards
/** Projects and writes one Module's normalized Affected Paths shards. */
final class AffectedPathReportDataWriter {

    /** Browser report data schema. */
    private static final int SCHEMA_VERSION = 5;

    /** JavaScript callback installed by the Affected Paths page. */
    private static final String CALLBACK =
            "window.__CIA_AFFECTED_PATH_SHARD__(";

    /** Shard suffix bytes after the record array. */
    private static final int SHARD_SUFFIX_BYTES = 5;

    /** Command diagnostic destination. */
    private final DiagnosticLog diagnostics;

    /** Maximum target bytes for a normal shard. */
    private final int maxShardBytes;

    /**
     * Creates a writer.
     *
     * @param log command diagnostic destination
     * @param shardBytes maximum target bytes for one normal shard
     */
    AffectedPathReportDataWriter(
            final DiagnosticLog log,
            final int shardBytes) {
        diagnostics = java.util.Objects.requireNonNull(log, "log");
        if (shardBytes <= 0) {
            throw new IllegalArgumentException("shardBytes must be positive");
        }
        maxShardBytes = shardBytes;
    }

    /**
     * Writes all data shards and returns the small browser manifest.
     *
     * @param module frozen Module analysis result
     * @param directory physical shard directory
     * @param relativeDirectory page-relative shard directory
     * @return small browser manifest
     * @throws IOException on shard publication failure
     */
    AffectedPathReportData write(
            final ModuleAnalysisResult module,
            final Path directory,
            final String relativeDirectory) throws IOException {
        Files.createDirectories(directory);
        final DiagnosticContext context = DiagnosticContext.of(
                "report", "affected-path-shard").withModule(
                        module.getModuleId().stableKey());
        diagnostics.debug(context, "started");
        final Projection projection = project(module);
        final Map<String, List<AffectedPathReportManifest.ShardDescriptor>>
                descriptors = new LinkedHashMap<>();
        final ShardMetrics metrics = new ShardMetrics();
        descriptors.put("index", writeShards("index",
                projection.paths().size(), index -> indexRecord(
                        projection.paths().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("source-index", writeShards("source-index",
                projection.sourceRanges().size(), index -> sourceRangeRecord(
                        projection.sourceRanges().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("rows", writeShards("rows",
                projection.rows().size(), index -> rowRecord(
                        projection.rows().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("paths", writeShards("paths",
                projection.paths().size(), index -> pathRecord(
                        projection.paths().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("methods", writeShards("methods",
                projection.methods().size(), index -> methodRecord(
                        projection.methods().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("members", writeShards("members",
                projection.members().size(), index -> memberRecord(
                        projection.members().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("dependencies", writeShards("dependencies",
                projection.dependencies().size(), index -> dependencyRecord(
                        projection.dependencies().get(index)), directory,
                relativeDirectory, context, metrics));
        descriptors.put("diffs", writeShards("diffs",
                projection.diffs().size(), index -> diffRecord(
                        projection.diffs().get(index)), directory,
                relativeDirectory, context, metrics));
        final String shardCounts = descriptors.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().size())
                .collect(Collectors.joining(","));
        diagnostics.debug(context, "completed; rows="
                + projection.rows().size() + "; paths="
                + projection.paths().size() + "; shardCounts="
                + shardCounts + "; shards=" + metrics.shards
                + "; bytes=" + metrics.bytes
                + "; maxShardBytes=" + metrics.maximum
                + "; oversizedShards=" + metrics.oversized);
        final AffectedPathReportManifest manifest =
                new AffectedPathReportManifest(
                SCHEMA_VERSION, projection.impactRows(),
                projection.rows().size() - projection.impactRows(),
                projection.sources(),
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(descriptors)));
        final Map<BoundChangePoint, AffectedPathReportData.MemberData>
                memberData = new LinkedHashMap<>();
        for (int index = 0; index < projection.memberKeys().size(); index++) {
            final MemberProjection member = projection.members().get(index);
            memberData.put(projection.memberKeys().get(index),
                    new AffectedPathReportData.MemberData(member.signature(),
                            member.diffStatus(), member.diffId()));
        }
        return new AffectedPathReportData(manifest,
                Collections.unmodifiableMap(memberData));
    }

    private Projection project(final ModuleAnalysisResult module) {
        final List<BoundChangePoint> members = module.getUnit()
                .getChangePoints().stream().distinct()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList();
        final Map<BoundChangePoint, Integer> memberIds = ids(members);
        final List<DependencyUpgradeKey> dependencies = members.stream()
                .map(BoundChangePoint::getDependencyUpgradeKey).distinct()
                .sorted(Comparator.comparing(DependencyUpgradeKey::stableKey))
                .toList();
        final Map<DependencyUpgradeKey, Integer> dependencyIds =
                ids(dependencies);
        final Map<String, ImpactPath> impact = new TreeMap<>();
        final Map<String, StructuralReferencePath> structural =
                new TreeMap<>();
        module.getImpactPaths().forEach(path -> impact.putIfAbsent(
                callPathKey(path), path));
        module.getStructuralPaths().forEach(path -> structural.putIfAbsent(
                structuralPathKey(path), path));

        final Map<String, MethodProjection> methodByKey = new TreeMap<>();
        impact.values().forEach(path -> collectMethods(
                path.getNodes(), methodByKey));
        structural.values().forEach(path -> collectMethods(
                path.getNodes(), methodByKey));
        final List<MethodProjection> methods = new ArrayList<>();
        final Map<String, Integer> methodIds = new LinkedHashMap<>();
        for (Map.Entry<String, MethodProjection> entry
                : methodByKey.entrySet()) {
            final int id = methods.size();
            methodIds.put(entry.getKey(), id);
            methods.add(entry.getValue().withId(id));
        }

        final Map<String, Set<Integer>> impactMembers = new TreeMap<>();
        module.getImpactPaths().forEach(path -> impactMembers
                .computeIfAbsent(callPathKey(path), ignored -> new TreeSet<>())
                .add(memberIds.get(path.getTerminal().getChangePoint())));
        final Map<String, Set<Integer>> structuralMembers = new TreeMap<>();
        module.getStructuralPaths().forEach(path -> structuralMembers
                .computeIfAbsent(structuralPathKey(path),
                        ignored -> new TreeSet<>())
                .add(memberIds.get(path.getChangePoint())));

        final List<PathProjection> paths = new ArrayList<>();
        final List<RowProjection> rows = new ArrayList<>();
        impact.forEach((key, path) -> addImpactPath(paths, rows, key, path,
                impactMembers.getOrDefault(key, Set.of()), methodIds));
        final int impactRows = rows.size();
        structural.forEach((key, path) -> addStructuralPath(
                paths, rows, key, path,
                structuralMembers.getOrDefault(key, Set.of()), methodIds));

        final Map<Integer, List<RowRangeProjection>> memberRanges =
                rowRangesByMember(rows, members.size());

        final List<DiffProjection> diffs = new ArrayList<>();
        final List<MemberProjection> memberRecords = new ArrayList<>();
        for (int id = 0; id < members.size(); id++) {
            final BoundChangePoint member = members.get(id);
            final CodeComparisonEvidence comparison =
                    module.getCodeComparisons().get(member);
            Integer diffId = null;
            final String status;
            if (comparison == null) {
                status = CodeComparisonStatus.UNAVAILABLE.name();
            } else {
                status = comparison.getStatus().name();
                if (comparison.getStatus() == CodeComparisonStatus.AVAILABLE) {
                    diffId = diffs.size();
                    diffs.add(new DiffProjection(
                            diffId, comparison.getUnifiedDiff()));
                }
            }
            memberRecords.add(new MemberProjection(id,
                    dependencyIds.get(member.getDependencyUpgradeKey()),
                    member.getChangePoint().getKind().name(),
                    ReportSignatures.changedMember(member.getChangePoint()),
                    status, diffId, memberRanges.get(id)));
        }
        final List<DependencyProjection> dependencyRecords =
                new ArrayList<>();
        for (int id = 0; id < dependencies.size(); id++) {
            final DependencyUpgradeKey dependency = dependencies.get(id);
            dependencyRecords.add(new DependencyProjection(id,
                    dependency.getOldArtifact().toString(),
                    dependency.getNewArtifact().toString(),
                    dependency.getScope().getValue(),
                    sourceKey(dependency)));
        }
        final Map<String, List<Integer>> rowsBySource = new TreeMap<>();
        for (RowProjection row : rows) {
            final DependencyUpgradeKey dependency = members.get(
                    row.memberId()).getDependencyUpgradeKey();
            rowsBySource.computeIfAbsent(sourceKey(dependency), ignored ->
                    new ArrayList<>()).add(row.id());
        }
        final List<SourceRangeProjection> sourceRanges = new ArrayList<>();
        final List<AffectedPathReportManifest.SourceDescriptor> sources =
                new ArrayList<>();
        rowsBySource.forEach((source, rowIds) -> {
            final int firstId = sourceRanges.size();
            int rangeStart = -1;
            int previous = -1;
            for (int rowId : rowIds) {
                if (rowId != previous + 1 && rangeStart >= 0) {
                    sourceRanges.add(new SourceRangeProjection(
                            sourceRanges.size(), rangeStart,
                            previous - rangeStart + 1));
                    rangeStart = rowId;
                } else if (rangeStart < 0) {
                    rangeStart = rowId;
                }
                previous = rowId;
            }
            if (rangeStart >= 0) {
                sourceRanges.add(new SourceRangeProjection(
                        sourceRanges.size(), rangeStart,
                        previous - rangeStart + 1));
            }
            sources.add(new AffectedPathReportManifest.SourceDescriptor(
                    source, firstId, sourceRanges.size() - firstId));
        });
        return new Projection(List.copyOf(paths), List.copyOf(rows),
                List.copyOf(methods), List.copyOf(memberRecords),
                List.copyOf(dependencyRecords), List.copyOf(diffs),
                List.copyOf(sourceRanges), List.copyOf(sources),
                List.copyOf(members), impactRows);
    }

    private String sourceKey(final DependencyUpgradeKey dependency) {
        return dependency.getNewArtifact().getGroupId() + ":"
                + dependency.getNewArtifact().getArtifactId();
    }

    private void addImpactPath(
            final List<PathProjection> paths,
            final List<RowProjection> rows,
            final String key,
            final ImpactPath path,
            final Set<Integer> members,
            final Map<String, Integer> methodIds) {
        final int pathId = paths.size();
        final int rowStart = rows.size();
        members.forEach(memberId -> rows.add(new RowProjection(
                rows.size(), pathId, memberId)));
        final List<String> affected = affectedMethods(path.getNodes(), "");
        paths.add(new PathProjection(pathId, "impact",
                path.getClassification().name(),
                path.getRootKind().name(),
                path.getRootKind()
                == ImpactPathRootKind.STRONGLY_CONNECTED_COMPONENT,
                "", "", "", methodIds(path.getNodes(), methodIds),
                affected, pathSearchText(path.getNodes(), List.of()), rowStart,
                rows.size() - rowStart, key));
    }

    private void addStructuralPath(
            final List<PathProjection> paths,
            final List<RowProjection> rows,
            final String key,
            final StructuralReferencePath path,
            final Set<Integer> members,
            final Map<String, Integer> methodIds) {
        final int pathId = paths.size();
        final int rowStart = rows.size();
        members.forEach(memberId -> rows.add(new RowProjection(
                rows.size(), pathId, memberId)));
        final String owner = structuralOwner(path);
        final String relation = path.getReference().getKind().getLabel();
        final String changedClass = path.getReference().getChangedClass()
                .replace('/', '.');
        final List<String> affected = affectedMethods(path.getNodes(), owner);
        paths.add(new PathProjection(pathId, "structural",
                path.getClassification().name(), "", false,
                owner, relation, changedClass,
                methodIds(path.getNodes(), methodIds),
                affected, pathSearchText(path.getNodes(),
                        List.of(owner, relation, changedClass)), rowStart,
                rows.size() - rowStart, key));
    }

    private List<Integer> methodIds(
            final List<QueryNode> nodes,
            final Map<String, Integer> ids) {
        return nodes.stream().map(node -> ids.get(
                methodKey(node.methodId()))).toList();
    }

    private List<String> affectedMethods(
            final List<QueryNode> nodes,
            final String fallback) {
        final Set<String> project = new LinkedHashSet<>();
        nodes.stream().filter(node -> node.origin() == CodeOrigin.PROJECT)
                .map(node -> ReportSignatures.method(node.methodId()))
                .forEach(project::add);
        if (!project.isEmpty()) {
            return List.copyOf(project);
        }
        return List.of(fallback.isBlank() ? "Unavailable" : fallback);
    }

    private String pathSearchText(
            final List<QueryNode> nodes,
            final List<String> structural) {
        final List<String> values = new ArrayList<>();
        nodes.stream().map(node -> ReportSignatures.method(node.methodId()))
                .forEach(values::add);
        values.addAll(structural);
        return String.join(" ", values);
    }

    private void collectMethods(
            final List<QueryNode> nodes,
            final Map<String, MethodProjection> methods) {
        for (QueryNode node : nodes) {
            final String key = methodKey(node.methodId());
            final boolean project = node.origin() == CodeOrigin.PROJECT;
            methods.merge(key, new MethodProjection(-1,
                            ReportSignatures.method(node.methodId()), project),
                    (left, right) -> new MethodProjection(-1, left.label(),
                            left.project() || right.project()));
        }
    }

    private Map<Integer, List<RowRangeProjection>> rowRangesByMember(
            final List<RowProjection> rows,
            final int memberCount) {
        final Map<Integer, List<Integer>> rowIds = new LinkedHashMap<>();
        for (int memberId = 0; memberId < memberCount; memberId++) {
            rowIds.put(memberId, new ArrayList<>());
        }
        rows.forEach(row -> rowIds.get(row.memberId()).add(row.id()));
        final Map<Integer, List<RowRangeProjection>> result =
                new LinkedHashMap<>();
        rowIds.forEach((memberId, ids) -> result.put(memberId,
                contiguousRanges(ids)));
        return result;
    }

    private List<RowRangeProjection> contiguousRanges(
            final List<Integer> rowIds) {
        if (rowIds.isEmpty()) {
            return List.of();
        }
        final List<RowRangeProjection> result = new ArrayList<>();
        int start = rowIds.get(0);
        int previous = start;
        for (int index = 1; index < rowIds.size(); index++) {
            final int value = rowIds.get(index);
            if (value != previous + 1) {
                result.add(new RowRangeProjection(
                        start, previous - start + 1));
                start = value;
            }
            previous = value;
        }
        result.add(new RowRangeProjection(start, previous - start + 1));
        return List.copyOf(result);
    }

    private <T> Map<T, Integer> ids(final List<T> values) {
        final Map<T, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            result.put(values.get(index), index);
        }
        return result;
    }

    private String callPathKey(final ImpactPath path) {
        return path.getClassification() + "|" + path.getRootKind() + "|"
                + path.getNodes().stream().map(node -> methodKey(
                        node.methodId())).collect(Collectors.joining("->"));
    }

    private String structuralPathKey(final StructuralReferencePath path) {
        return path.getClassification() + "|" + structuralOwner(path) + "|"
                + path.getReference().getKind() + "|"
                + path.getReference().getChangedClass() + "|"
                + path.getNodes().stream().map(node -> methodKey(
                        node.methodId())).collect(Collectors.joining("->"));
    }

    private String methodKey(final MethodId method) {
        return method.module() + "|" + method.sourceId() + "|"
                + method.owner() + "|" + method.name() + "|"
                + method.descriptor();
    }

    private String structuralOwner(final StructuralReferencePath path) {
        return ReportSignatures.structuralOwner(path.getReference());
    }

    private List<AffectedPathReportManifest.ShardDescriptor> writeShards(
            final String kind,
            final int count,
            final IntFunction<JsonRecord> records,
            final Path directory,
            final String relativeDirectory,
            final DiagnosticContext context,
            final ShardMetrics metrics) throws IOException {
        final List<AffectedPathReportManifest.ShardDescriptor> result =
                new ArrayList<>();
        final List<byte[]> pending = new ArrayList<>();
        int pendingBytes = 0;
        int firstId = 0;
        for (int id = 0; id < count; id++) {
            final byte[] record = serialize(records.apply(id));
            final int wrapper = wrapperBytes(kind, result.size());
            final int separator = pending.isEmpty() ? 0 : 1;
            if (!pending.isEmpty() && wrapper + pendingBytes
                    + separator + record.length > maxShardBytes) {
                result.add(flush(kind, result.size(), firstId,
                        pending, new ShardWriteContext(directory,
                                relativeDirectory, metrics)));
                pending.clear();
                pendingBytes = 0;
                firstId = id;
            }
            pending.add(record);
            pendingBytes += record.length + (pending.size() == 1 ? 0 : 1);
        }
        if (!pending.isEmpty()) {
            result.add(flush(kind, result.size(), firstId,
                    pending, new ShardWriteContext(directory,
                            relativeDirectory, metrics)));
        }
        for (int index = 0; index < result.size(); index++) {
            final AffectedPathReportManifest.ShardDescriptor descriptor =
                    result.get(index);
            diagnostics.trace(context, "written; kind=" + kind
                    + "; progress=" + (index + 1) + "/" + result.size()
                    + "; records=" + descriptor.records() + "; bytes="
                    + descriptor.bytes());
        }
        return List.copyOf(result);
    }

    private AffectedPathReportManifest.ShardDescriptor flush(
            final String kind,
            final int shardId,
            final int firstId,
            final List<byte[]> records,
            final ShardWriteContext context) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(prefix(kind, shardId));
        for (int index = 0; index < records.size(); index++) {
            if (index > 0) {
                output.write(',');
            }
            output.write(records.get(index));
        }
        output.write("]});\n".getBytes(StandardCharsets.UTF_8));
        final byte[] bytes = output.toByteArray();
        final String file = kind + "-" + String.format(
                java.util.Locale.ROOT, "%05d", shardId) + ".js";
        Files.write(context.directory().resolve(file), bytes);
        context.metrics().add(bytes.length, bytes.length > maxShardBytes);
        return new AffectedPathReportManifest.ShardDescriptor(
                shardId, context.relativeDirectory() + "/" + file,
                firstId, firstId + records.size() - 1,
                records.size(), bytes.length);
    }

    private int wrapperBytes(final String kind, final int shardId) {
        return prefix(kind, shardId).length + SHARD_SUFFIX_BYTES;
    }

    private byte[] prefix(final String kind, final int shardId) {
        return (CALLBACK + "{\"schemaVersion\":" + SCHEMA_VERSION
                + ",\"kind\":\"" + kind + "\",\"shardId\":"
                + shardId + ",\"records\":[")
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] serialize(final JsonRecord record) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator json = ScriptSafeJson.factory()
                .createGenerator(output)) {
            record.write(json);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    private JsonRecord indexRecord(final PathProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("pathId", value.id());
            json.writeStringField("type", value.type());
            json.writeStringField("searchText", value.searchText());
            json.writeArrayFieldStart("affectedMethods");
            for (String method : value.affectedMethods()) {
                json.writeString(method);
            }
            json.writeEndArray();
            json.writeNumberField("rowStart", value.rowStart());
            json.writeNumberField("rowCount", value.rowCount());
            json.writeEndObject();
        };
    }

    private JsonRecord rowRecord(final RowProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("rowId", value.id());
            json.writeNumberField("pathId", value.pathId());
            json.writeNumberField("changedMemberId", value.memberId());
            json.writeEndObject();
        };
    }

    private JsonRecord sourceRangeRecord(
            final SourceRangeProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("rowStart", value.rowStart());
            json.writeNumberField("rowCount", value.rowCount());
            json.writeEndObject();
        };
    }

    private JsonRecord pathRecord(final PathProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeStringField("type", value.type());
            json.writeStringField("classification", value.classification());
            json.writeStringField("rootKind", value.rootKind());
            json.writeBooleanField("cycle", value.cycle());
            json.writeStringField("applicationMember",
                    value.applicationMember());
            json.writeStringField("relation", value.relation());
            json.writeStringField("changedClass", value.changedClass());
            json.writeArrayFieldStart("methodIds");
            value.methodIds().forEach(id -> {
                try {
                    json.writeNumber(id);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
            json.writeEndArray();
            json.writeEndObject();
        };
    }

    private JsonRecord methodRecord(final MethodProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeStringField("label", value.label());
            json.writeBooleanField("project", value.project());
            json.writeEndObject();
        };
    }

    private JsonRecord memberRecord(final MemberProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeNumberField("dependencyUpgradeId",
                    value.dependencyId());
            json.writeStringField("changePointKind", value.kind());
            json.writeStringField("signature", value.signature());
            json.writeStringField("codeDiffStatus", value.diffStatus());
            if (value.diffId() == null) {
                json.writeNullField("codeDiffId");
            } else {
                json.writeNumberField("codeDiffId", value.diffId());
            }
            json.writeArrayFieldStart("rowRanges");
            for (RowRangeProjection range : value.rowRanges()) {
                json.writeStartObject();
                json.writeNumberField("start", range.start());
                json.writeNumberField("count", range.count());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        };
    }

    private JsonRecord dependencyRecord(final DependencyProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeStringField("oldArtifact", value.oldArtifact());
            json.writeStringField("newArtifact", value.newArtifact());
            json.writeStringField("scope", value.scope());
            json.writeStringField("source", value.source());
            json.writeEndObject();
        };
    }

    private JsonRecord diffRecord(final DiffProjection value) {
        return json -> {
            json.writeStartObject();
            json.writeNumberField("id", value.id());
            json.writeStringField("unifiedDiff", value.unifiedDiff());
            json.writeEndObject();
        };
    }

    /** One JSON object writer. */
    @FunctionalInterface
    private interface JsonRecord {
        void write(JsonGenerator json) throws IOException;
    }

    /** Mutable write metrics. */
    private static final class ShardMetrics {
        /** Total shards. */
        private int shards;
        /** Total shard bytes. */
        private long bytes;
        /** Maximum shard bytes. */
        private long maximum;
        /** Shards exceeding the target due to one record. */
        private int oversized;

        void add(final long size, final boolean isOversized) {
            shards++;
            bytes += size;
            maximum = Math.max(maximum, size);
            if (isOversized) {
                oversized++;
            }
        }
    }

    /**
     * Immutable output state shared while flushing one shard.
     *
     * @param directory physical shard directory
     * @param relativeDirectory page-relative shard directory
     * @param metrics mutable aggregate metrics
     */
    private record ShardWriteContext(
            Path directory,
            String relativeDirectory,
            ShardMetrics metrics) {
    }

    private record Projection(
            List<PathProjection> paths,
            List<RowProjection> rows,
            List<MethodProjection> methods,
            List<MemberProjection> members,
            List<DependencyProjection> dependencies,
            List<DiffProjection> diffs,
            List<SourceRangeProjection> sourceRanges,
            List<AffectedPathReportManifest.SourceDescriptor> sources,
            List<BoundChangePoint> memberKeys,
            int impactRows) {
    }

    private record PathProjection(
            int id,
            String type,
            String classification,
            String rootKind,
            boolean cycle,
            String applicationMember,
            String relation,
            String changedClass,
            List<Integer> methodIds,
            List<String> affectedMethods,
            String searchText,
            int rowStart,
            int rowCount,
            String stableKey) {
    }

    private record RowProjection(int id, int pathId, int memberId) {
    }

    private record MethodProjection(int id, String label, boolean project) {
        MethodProjection withId(final int value) {
            return new MethodProjection(value, label, project);
        }
    }

    private record MemberProjection(
            int id,
            int dependencyId,
            String kind,
            String signature,
            String diffStatus,
            Integer diffId,
            List<RowRangeProjection> rowRanges) {
    }

    private record DependencyProjection(
            int id,
            String oldArtifact,
            String newArtifact,
            String scope,
            String source) {
    }

    private record SourceRangeProjection(
            int id,
            int rowStart,
            int rowCount) {
    }

    private record RowRangeProjection(int start, int count) {
    }

    private record DiffProjection(int id, String unifiedDiff) {
    }
}
