package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;

import io.github.dependencyanalysis.bytecode.JvmAccess;

import java.util.Objects;

/**
 * Typed target-side JVM access query.
 *
 * @param caller caller class
 * @param declaringClass resolved declaration class
 * @param symbolicOwner constant-pool symbolic owner
 * @param newAccess target normalized access
 * @param referenceKind JVM reference operation
 * @param receiverType local verifier receiver abstraction
 */
public record AccessCheckRequest(
        IClass caller,
        IClass declaringClass,
        IClass symbolicOwner,
        JvmAccess newAccess,
        JvmReferenceKind referenceKind,
        ReceiverType receiverType) {

    /** Validates the typed request. */
    public AccessCheckRequest {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(declaringClass, "declaringClass");
        Objects.requireNonNull(symbolicOwner, "symbolicOwner");
        Objects.requireNonNull(newAccess, "newAccess");
        Objects.requireNonNull(referenceKind, "referenceKind");
        Objects.requireNonNull(receiverType, "receiverType");
    }
}
