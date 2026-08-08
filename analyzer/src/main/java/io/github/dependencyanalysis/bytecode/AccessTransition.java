package io.github.dependencyanalysis.bytecode;

import java.util.Objects;

/**
 * Validated strict JVM access narrowing.
 *
 * @param oldAccess previous normalized access
 * @param newAccess target normalized access
 */
public record AccessTransition(
        JvmAccess oldAccess,
        JvmAccess newAccess) {

    /** Validates that the transition is strictly narrowing. */
    public AccessTransition {
        Objects.requireNonNull(oldAccess, "oldAccess");
        Objects.requireNonNull(newAccess, "newAccess");
        if (!oldAccess.narrowsTo(newAccess)) {
            throw new IllegalArgumentException(
                    "Access transition must be strictly narrowing: "
                            + oldAccess + "->" + newAccess);
        }
    }

    /** @return stable transition identity */
    public String stableKey() {
        return oldAccess + "->" + newAccess;
    }
}
