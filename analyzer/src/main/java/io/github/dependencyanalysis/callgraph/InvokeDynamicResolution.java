package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IMethod;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of decoding one reachable invokedynamic callsite.
 *
 * @param disposition selector action
 * @param target exact registered target when modeled
 * @param evidence decoded typed evidence
 * @param limitations decoded typed limitations
 */
record InvokeDynamicResolution(
        Disposition disposition,
        Optional<IMethod> target,
        List<DynamicCallEvidence> evidence,
        List<ModelLimitation> limitations) {

    /** Selector action after recording typed metadata. */
    enum Disposition {
        /** Delegate to the previously installed WALA selector. */
        DELEGATE,
        /** Return the exact registered model target. */
        TARGET,
        /** Registered protocol was recognized but could not be modeled. */
        NO_TARGET
    }

    InvokeDynamicResolution {
        Objects.requireNonNull(disposition, "disposition");
        target = Objects.requireNonNull(target, "target");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        limitations = List.copyOf(Objects.requireNonNull(
                limitations, "limitations"));
        if ((disposition == Disposition.TARGET) != target.isPresent()) {
            throw new IllegalArgumentException(
                    "Only TARGET resolutions may contain a target");
        }
    }
}
