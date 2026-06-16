package io.github.changeimpact.analyze.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a Maven build
 * containing all module outputs.
 */
public final class BuildResult {

    /** Module build outputs. */
    private final List<ModuleBuildOutput>
            outputs;

    private BuildResult(
            final List<ModuleBuildOutput>
                    list) {
        this.outputs =
                Collections.unmodifiableList(
                        new ArrayList<>(list));
    }

    /**
     * Creates a new build result.
     *
     * @param list module outputs
     * @return new instance
     */
    public static BuildResult of(
            final List<ModuleBuildOutput>
                    list) {
        Objects.requireNonNull(list, "list");
        return new BuildResult(list);
    }

    /**
     * Returns unmodifiable list of module
     * build outputs.
     *
     * @return module build outputs
     */
    public List<ModuleBuildOutput>
            getOutputs() {
        return outputs;
    }

    @Override
    public String toString() {
        return "BuildResult{"
                + "outputs=" + outputs
                + '}';
    }
}
