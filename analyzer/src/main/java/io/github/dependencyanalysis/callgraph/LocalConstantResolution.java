package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.types.TypeReference;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of bounded caller-local SSA constant recovery.
 *
 * @param status resolution status
 * @param stringValue resolved String constant
 * @param classValue resolved Class constant
 * @param detail stable resolution detail
 */
public record LocalConstantResolution(
        Status status,
        Optional<String> stringValue,
        Optional<TypeReference> classValue,
        String detail) {

    /** Resolution outcome. */
    public enum Status {
        /** One exact constant was recovered. */
        RESOLVED,
        /** The value is in scope but outside the bounded contract. */
        UNRESOLVED,
        /** The requested constant kind does not apply. */
        NOT_APPLICABLE
    }

    /** Validates the exclusive constant payload. */
    public LocalConstantResolution {
        Objects.requireNonNull(status, "status");
        stringValue = Objects.requireNonNull(stringValue, "stringValue");
        classValue = Objects.requireNonNull(classValue, "classValue");
        Objects.requireNonNull(detail, "detail");
        if (stringValue.isPresent() && classValue.isPresent()) {
            throw new IllegalArgumentException(
                    "Local constant resolution must have one value kind");
        }
        if (status == Status.RESOLVED
                && stringValue.isEmpty() && classValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "Resolved local constant requires one value");
        }
        if (status != Status.RESOLVED
                && (stringValue.isPresent() || classValue.isPresent())) {
            throw new IllegalArgumentException(
                    "Unresolved local constant cannot retain a value");
        }
    }

    static LocalConstantResolution string(final String value) {
        return new LocalConstantResolution(Status.RESOLVED,
                Optional.of(value), Optional.empty(), "resolved-string");
    }

    static LocalConstantResolution type(final TypeReference value) {
        return new LocalConstantResolution(Status.RESOLVED,
                Optional.empty(), Optional.of(value), "resolved-class");
    }

    static LocalConstantResolution unresolved(final String reason) {
        return new LocalConstantResolution(Status.UNRESOLVED,
                Optional.empty(), Optional.empty(), reason);
    }
}
