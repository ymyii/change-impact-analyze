package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the fixed Java-first method body suppression contract. */
class DecompileComparisonEvidenceTest {

    /** Example Java 8 class major version. */
    private static final int CLASS_MAJOR_VERSION = 52;

    /** Example comparison elapsed milliseconds. */
    private static final long ELAPSED_MILLIS = 3L;

    /** Baseline artifact. */
    private static final ArtifactCoord OLD =
            new ArtifactCoord("g", "a", "jar", "1");

    /** Target artifact. */
    private static final ArtifactCoord NEW =
            new ArtifactCoord("g", "a", "jar", "2");

    @Test
    void identicalJavaSuppressesBeforeSsa() {
        final DecompileComparisonEvidence evidence = evidence(
                DecompiledMethod.available("int value() { return 1; }\n"),
                DecompiledMethod.available("int value() { return 1; }\n"));

        assertThat(evidence.getStatus())
                .isEqualTo(DecompileComparisonStatus.IDENTICAL);
        assertThat(evidence.isSuppressed()).isTrue();
        assertThat(evidence.getSuppressionReasons()).containsExactly(
                MethodBodySuppressionReason.JAVA_TEXT_IDENTICAL);
        assertThatThrownBy(() -> evidence.withSsaMatched(
                ssa(SsaComparisonStatus.MATCHED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("short-circuited");
    }

    @Test
    void matchedSsaSuppressesDifferentJava() {
        final DecompileComparisonEvidence evidence = evidence(
                DecompiledMethod.available("return 1;\n"),
                DecompiledMethod.available("return 2;\n"))
                .withSsaMatched(ssa(SsaComparisonStatus.MATCHED));

        assertThat(evidence.getStatus())
                .isEqualTo(DecompileComparisonStatus.DIFFERENT);
        assertThat(evidence.getSuppressionReasons()).containsExactly(
                MethodBodySuppressionReason.SSA_MATCHED);
    }

    @Test
    void differentJavaDoesNotSuppressBeforeSsa() {
        final DecompileComparisonEvidence evidence = evidence(
                DecompiledMethod.available("return 1;\n"),
                DecompiledMethod.available("return 2;\n"));

        assertThat(evidence.getSuppressionReasons()).isEmpty();
        assertThat(evidence.isSuppressed()).isFalse();
    }

    @Test
    void unavailableJavaAndUnknownSsaRetainCandidate() {
        final DecompileComparisonEvidence evidence = evidence(
                DecompiledMethod.unavailable("old failed"),
                DecompiledMethod.unavailable("new failed"));

        assertThat(evidence.getStatus())
                .isEqualTo(DecompileComparisonStatus.UNKNOWN);
        assertThat(evidence.getSuppressionReasons()).isEmpty();
        assertThat(evidence.isSuppressed()).isFalse();
        assertThat(evidence.summary().suppressionReasons()).isEmpty();
    }

    private DecompileComparisonEvidence evidence(
            final DecompiledMethod oldMethod,
            final DecompiledMethod newMethod) {
        return new DecompileComparisonEvidence(OLD, point(),
                CLASS_MAJOR_VERSION, CLASS_MAJOR_VERSION,
                oldMethod, newMethod, ELAPSED_MILLIS);
    }

    private SsaComparisonEvidence ssa(
            final SsaComparisonStatus ssaStatus) {
        return new SsaComparisonEvidence(change(), point(),
                CLASS_MAJOR_VERSION, CLASS_MAJOR_VERSION,
                ssaStatus, ssaStatus.name(), ELAPSED_MILLIS);
    }

    private ChangePoint point() {
        return ChangePoint.withDescriptors(NEW,
                ChangePointKind.METHOD_BODY_CHANGED, "example/Foo", "value",
                new MemberDescriptors("()I", "()I"), "old", "new");
    }

    private DependencyChange change() {
        return new DependencyChange(
                ChangeType.VERSION_CHANGED, OLD, NEW,
                DependencyScope.COMPILE, "module");
    }
}
