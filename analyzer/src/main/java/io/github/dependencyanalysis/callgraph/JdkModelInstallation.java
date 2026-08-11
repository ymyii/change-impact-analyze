package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;

import io.github.dependencyanalysis.models.jdk.JdkModelException;
import io.github.dependencyanalysis.models.jdk.JdkModelMetadata;
import io.github.dependencyanalysis.models.jdk.JdkModelSession;
import io.github.dependencyanalysis.models.jdk8.Jdk8Models;

import java.util.Objects;
import java.util.Optional;

/** One per-graph JDK Method Model installation. */
final class JdkModelInstallation {

    /** Installed model session, absent for none. */
    private final JdkModelSession session;

    private JdkModelInstallation(final JdkModelSession installedSession) {
        session = installedSession;
    }

    // Wiki: wiki/features/jdk-method-models.md - impact installation boundary.
    /**
     * Installs the selected model after WALA default bypass configuration.
     *
     * @param selection command-wide model selection
     * @param options configured WALA options
     * @param hierarchy active target hierarchy
     * @return per-graph installation
     */
    static JdkModelInstallation install(
            final JdkModelSelection selection,
            final AnalysisOptions options,
            final IClassHierarchy hierarchy) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(hierarchy, "hierarchy");
        if (selection == JdkModelSelection.NONE) {
            return new JdkModelInstallation(null);
        }
        if (!Jdk8Models.supports(hierarchy)) {
            throw new JdkModelException(
                    "JDK 8 model requires WALA's Synthetic loader");
        }
        final JdkModelSession installed;
        try {
            installed = Jdk8Models.install(options, hierarchy);
        } catch (RuntimeException exception) {
            throw new JdkModelException(
                    "JDK 8 model installation failed", exception);
        }
        final JdkModelMetadata initial = installed.snapshot();
        if (initial.unavailableTargetCount() != 0) {
            throw new JdkModelException(
                    "JDK 8 model catalog is incomplete in the active "
                            + "hierarchy");
        }
        return new JdkModelInstallation(installed);
    }

    /** @return post-fixed-point immutable model metadata */
    Optional<JdkModelMetadata> snapshot() {
        return Optional.ofNullable(session).map(JdkModelSession::snapshot);
    }
}
