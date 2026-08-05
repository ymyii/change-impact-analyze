package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IMethod;

import java.util.Objects;
import java.util.Optional;

/** Result from one exact invokedynamic bootstrap model. */
public final class InvokeDynamicModelResult {

    /** Synthetic fixed-point target, nullable for unsupported. */
    private final IMethod target;

    /** Unsupported reason, nullable for modeled. */
    private final String limitation;

    private InvokeDynamicModelResult(
            final IMethod method,
            final String reason) {
        target = method;
        limitation = reason;
    }

    /**
     * Creates a modeled result.
     *
     * @param method synthetic target
     * @return modeled result
     */
    public static InvokeDynamicModelResult modeled(final IMethod method) {
        return new InvokeDynamicModelResult(
                Objects.requireNonNull(method, "method"), null);
    }

    /**
     * Creates an explicit unsupported result.
     *
     * @param reason stable limitation reason
     * @return unsupported result
     */
    public static InvokeDynamicModelResult unsupported(
            final String reason) {
        return new InvokeDynamicModelResult(null,
                Objects.requireNonNull(reason, "reason"));
    }

    /** @return modeled target */
    public Optional<IMethod> target() {
        return Optional.ofNullable(target);
    }

    /** @return unsupported limitation */
    public Optional<String> limitation() {
        return Optional.ofNullable(limitation);
    }
}
