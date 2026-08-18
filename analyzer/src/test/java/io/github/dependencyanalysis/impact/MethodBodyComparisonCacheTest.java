package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.BytecodeDiffResult;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.DecompileComparisonEvidence;
import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.bytecode.SsaComparisonStatus;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.runtime.CommandRunDirectory;
import io.github.dependencyanalysis.runtime.ReportCache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests command-owned method body comparison cache contracts. */
class MethodBodyComparisonCacheTest {

    /** Example Java 8 class major version. */
    private static final int CLASS_MAJOR_VERSION = 52;

    /** Example SSA comparison elapsed milliseconds. */
    private static final long SSA_ELAPSED_MILLIS = 3L;

    /** Example decompilation comparison elapsed milliseconds. */
    private static final long DECOMPILE_ELAPSED_MILLIS = 4L;

    /** Test config root. */
    @TempDir
    private Path config;

    /** Baseline artifact. */
    private static final ArtifactCoord OLD =
            new ArtifactCoord("g", "library", "jar", "1");

    /** Target artifact. */
    private static final ArtifactCoord NEW =
            new ArtifactCoord("g", "library", "jar", "2");

    @Test
    void writesStableSourceCacheAndBuildsDiffWithoutJarAccess()
            throws Exception {
        final ChangePoint second = point("second", "old-second", "new-second");
        final ChangePoint first = point("first", "old-first", "new-first");
        final Path cacheRoot;
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            cacheRoot = reportCache.root();
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), result(second, first));

            final ReportCache.Fragment fragment = reportCache.fragments()
                    .get(0);
            final String content = Files.readString(fragment.path());
            assertThat(fragment.kind()).isEqualTo(
                    "method-body-comparison");
            assertThat(fragment.recordCount()).isEqualTo(2);
            assertThat(fragment.completeMarker()).isRegularFile();
            assertThat(content.indexOf("\"name\":\"first\""))
                    .isLessThan(content.indexOf("\"name\":\"second\""));
            assertThat(content).doesNotContain(config.toString())
                    .doesNotContain("oldJar")
                    .doesNotContain("newJar");

            final CodeComparisonEvidence comparison = cache.codeComparison(
                    bound(first));
            assertThat(comparison.getStatus())
                    .isEqualTo(CodeComparisonStatus.AVAILABLE);
            assertThat(comparison.getUnifiedDiff())
                    .contains("-return 1;")
                    .contains("+return 2;");
            reportCache.complete();
        }
        assertThat(cacheRoot).doesNotExist();
    }

    @Test
    void unknownDecompilationReturnsUnavailableWithoutRetry() {
        final ChangePoint point = point("unavailable", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), result(point,
                    DecompiledMethod.unavailable("old failed"),
                    DecompiledMethod.available("return 2;\n")));

            final CodeComparisonEvidence comparison = cache.codeComparison(
                    bound(point));

            assertThat(comparison.getStatus())
                    .isEqualTo(CodeComparisonStatus.UNAVAILABLE);
            assertThat(comparison.getReason()).contains("old failed");
        }
    }

    @Test
    void recordsJavaShortCircuitWithoutSsaEvidence() throws Exception {
        final ChangePoint point = point("identical", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            final DecompileComparisonEvidence identical = evidence(point,
                    DecompiledMethod.available("return 1;\n"),
                    DecompiledMethod.available("return 1;\n"));
            cache.write(change(), new BytecodeDiffResult(
                    List.of(), 1, List.of(), List.of(identical)));

            final String content = Files.readString(
                    reportCache.fragments().get(0).path());
            assertThat(content)
                    .contains("\"schemaVersion\":2")
                    .contains("\"ssaExecuted\":false")
                    .contains("\"ssaStatus\":\"NOT_EXECUTED\"")
                    .contains("JAVA_TEXT_IDENTICAL_SHORT_CIRCUIT")
                    .contains("\"ssaElapsedMillis\":0");
        }
    }

    @Test
    void corruptFragmentFailsFast() throws Exception {
        final ChangePoint point = point("value", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), result(point));
            Files.writeString(reportCache.fragments().get(0).path(), "{");

            assertThatThrownBy(() -> cache.codeComparison(bound(point)))
                    .isInstanceOf(MethodBodyCacheException.class)
                    .hasMessageContaining(
                            "Unable to read method body comparison cache");
        }
    }

    @Test
    void inconsistentEvidenceFailsFast() throws Exception {
        final ChangePoint point = point("value", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), result(point));
            final Path fragment = reportCache.fragments().get(0).path();
            Files.writeString(fragment, Files.readString(fragment).replace(
                    "\"decompileStatus\":\"DIFFERENT\"",
                    "\"decompileStatus\":\"IDENTICAL\""));

            assertThatThrownBy(() -> cache.codeComparison(bound(point)))
                    .isInstanceOf(MethodBodyCacheException.class)
                    .hasMessageContaining(
                            "Invalid method body cache fragment");
        }
    }

    @Test
    void missingFragmentFailsFast() {
        final ChangePoint point = point("value", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);

            assertThatThrownBy(() -> cache.codeComparison(bound(point)))
                    .isInstanceOf(MethodBodyCacheException.class)
                    .hasMessageContaining(
                            "Missing method body cache fragment");
        }
    }

    @Test
    void incompleteFragmentFailsFast() throws Exception {
        final ChangePoint point = point("value", "old", "new");
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), result(point));
            Files.delete(reportCache.fragments().get(0).completeMarker());

            assertThatThrownBy(() -> cache.codeComparison(bound(point)))
                    .isInstanceOf(MethodBodyCacheException.class)
                    .hasMessageContaining(
                            "Unable to read method body comparison cache");
        }
    }

    @Test
    void writesOneEmptyFragmentForPairWithoutEligibleMethods() {
        try (CommandRunDirectory run =
                     new CommandRunDirectory(config, "impact");
             ReportCache reportCache = new ReportCache(run, "impact")) {
            final MethodBodyComparisonCache cache =
                    new MethodBodyComparisonCache(reportCache);
            cache.write(change(), new BytecodeDiffResult(
                    List.of(), 0, List.of(), List.of()));

            assertThat(reportCache.fragments()).singleElement()
                    .satisfies(fragment -> {
                        assertThat(fragment.kind()).isEqualTo(
                                "method-body-comparison");
                        assertThat(fragment.recordCount()).isZero();
                    });
        }
    }

    private BytecodeDiffResult result(final ChangePoint... points) {
        final List<SsaComparisonEvidence> ssa = List.of(points).stream()
                .map(point -> ssa(point, SsaComparisonStatus.DIFFERENT))
                .toList();
        final List<DecompileComparisonEvidence> decompiled =
                List.of(points).stream().map(point -> evidence(point,
                        DecompiledMethod.available("return 1;\n"),
                        DecompiledMethod.available("return 2;\n")))
                        .toList();
        return new BytecodeDiffResult(List.of(points), points.length,
                ssa, decompiled);
    }

    private BytecodeDiffResult result(
            final ChangePoint point,
            final DecompiledMethod oldMethod,
            final DecompiledMethod newMethod) {
        return new BytecodeDiffResult(List.of(point), 1,
                List.of(ssa(point, SsaComparisonStatus.UNKNOWN)),
                List.of(new DecompileComparisonEvidence(OLD, point,
                        CLASS_MAJOR_VERSION, CLASS_MAJOR_VERSION,
                        oldMethod, newMethod,
                        DECOMPILE_ELAPSED_MILLIS)));
    }

    private DecompileComparisonEvidence evidence(
            final ChangePoint point,
            final DecompiledMethod oldMethod,
            final DecompiledMethod newMethod) {
        return new DecompileComparisonEvidence(OLD, point,
                CLASS_MAJOR_VERSION, CLASS_MAJOR_VERSION,
                oldMethod, newMethod, DECOMPILE_ELAPSED_MILLIS);
    }

    private SsaComparisonEvidence ssa(
            final ChangePoint point,
            final SsaComparisonStatus status) {
        return new SsaComparisonEvidence(change(), point,
                CLASS_MAJOR_VERSION, CLASS_MAJOR_VERSION, status,
                status.name(), SSA_ELAPSED_MILLIS);
    }

    private ChangePoint point(
            final String name,
            final String oldHash,
            final String newHash) {
        return ChangePoint.withDescriptors(NEW,
                ChangePointKind.METHOD_BODY_CHANGED,
                "example/Example", name,
                new MemberDescriptors("()I", "()I"),
                oldHash, newHash);
    }

    private DependencyChange change() {
        return new DependencyChange(ChangeType.VERSION_CHANGED, OLD, NEW,
                DependencyScope.COMPILE, "module");
    }

    private BoundChangePoint bound(final ChangePoint point) {
        final ModuleId module = new ModuleId(
                new ArtifactCoord("g", "module", "jar", "1"),
                Path.of("module"));
        return new BoundChangePoint(new DependencyUpgradeKey(module,
                DependencyScope.COMPILE, OLD, NEW), point);
    }
}
