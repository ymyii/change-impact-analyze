package io.github.dependencyanalysis.callgraph.jdk;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarFile;

/** Prepares the target JDK scope used by WALA analysis. */
public final class JdkAnalysisStage {

    /** Diagnostic stage name. */
    public static final String STAGE = "jdk-analysis";

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates the stage.
     *
     * @param collector diagnostics
     */
    public JdkAnalysisStage(
            final DiagnosticLog collector) {
        diagnostics = Objects.requireNonNull(
                collector, "collector");
    }

    /**
     * Builds a base scope from the selected target JDK.
     *
     * @param runtime target JDK descriptor
     * @return prepared WALA scope
     */
    public AnalysisScope prepare(
            final JavaRuntimeDescriptor runtime) {
        Objects.requireNonNull(runtime, "runtime");
        diagnostics.startStage(STAGE);
        try {
            final AnalysisScope scope =
                    AnalysisScope.createJavaAnalysisScope();
            addJars(scope,
                    ClassLoaderReference.Primordial,
                    runtime.getBootClassPath());
            addJars(scope,
                    ClassLoaderReference.Extension,
                    runtime.getExtensionClassPath());
            diagnostics.info(STAGE,
                    "Target JDK: " + runtime.getVersion()
                            + " at " + runtime.getJavaHome());
            diagnostics.info(STAGE,
                    "Scope entries: Primordial="
                            + runtime.getBootClassPath().size()
                            + ", Extension="
                            + runtime.getExtensionClassPath().size());
            diagnostics.endStage(STAGE);
            return scope;
        } catch (RuntimeException exception) {
            diagnostics.failStage(STAGE,
                    "reason=Target JDK scope failed: "
                            + Objects.requireNonNullElse(
                            exception.getMessage(),
                            exception.getClass().getName()));
            throw exception;
        }
    }

    private void addJars(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final Iterable<Path> paths) {
        for (Path path : paths) {
            try {
                scope.addToScope(loader,
                        new JarFile(path.toFile(), false));
            } catch (IOException exception) {
                throw new CallGraphException(
                        "Unable to read target JDK entry: "
                                + path,
                        exception);
            }
        }
    }
}
