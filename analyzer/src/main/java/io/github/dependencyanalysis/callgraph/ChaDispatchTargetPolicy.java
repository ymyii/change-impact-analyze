package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable Diff-directed target-retention policy for CHA Object dispatch. */
final class ChaDispatchTargetPolicy {

    /** Exact Object.toString selector. */
    private static final Selector TO_STRING =
            Selector.make("toString()Ljava/lang/String;");

    /** Exact Object.hashCode selector. */
    private static final Selector HASH_CODE = Selector.make("hashCode()I");

    /** Whether filtering is enabled. */
    private final boolean enabled;

    /** Target external methods whose override Diff survives in target. */
    private final Set<ExternalMethodKey> diffRelatedExternalMethods;

    /** Target classpath winner ownership, frozen before strategy build. */
    private final ClassOwnershipIndex ownership;

    private ChaDispatchTargetPolicy(
            final boolean filterEnabled,
            final Set<ExternalMethodKey> externalMethods,
            final ClassOwnershipIndex winnerOwnership) {
        enabled = filterEnabled;
        diffRelatedExternalMethods = Set.copyOf(Objects.requireNonNull(
                externalMethods, "externalMethods"));
        ownership = Objects.requireNonNull(
                winnerOwnership, "winnerOwnership");
    }

    /**
     * Builds a policy from target-side module Diff and winner ownership.
     *
     * @param unit module analysis input
     * @param ownership target classpath winner ownership
     * @return immutable enabled policy
     */
    static ChaDispatchTargetPolicy create(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(ownership, "ownership");
        final Set<ExternalMethodKey> methods = new LinkedHashSet<>();
        for (BoundChangePoint bound : unit.getChangePoints()) {
            final ChangePoint point = bound.getChangePoint();
            if (!targetMethodSurvives(point.getKind())
                    || !protectedSelector(
                    point.getName(), point.getNewDescriptor())) {
                continue;
            }
            final ClassOwnership winner = ownership.ownershipOf(
                    point.getOwner());
            if (winner == null
                    || winner.getOrigin() != CodeOrigin.DEPENDENCY
                    || winner.getSource().artifact().isEmpty()
                    || !winner.getSource().artifact().get().equals(
                    point.getArtifact())) {
                continue;
            }
            methods.add(new ExternalMethodKey(point.getArtifact(),
                    normalizeOwner(point.getOwner()), point.getName(),
                    point.getNewDescriptor()));
        }
        return new ChaDispatchTargetPolicy(true, methods, ownership);
    }

    /** @return compatibility policy that leaves all dispatch unchanged */
    static ChaDispatchTargetPolicy disabled() {
        return new ChaDispatchTargetPolicy(false, Set.of(),
                new ClassOwnershipIndex());
    }

    /**
     * @param declaredTarget declared virtual target
     * @return whether this exact Object dispatch is filtered
     */
    boolean filters(final MethodReference declaredTarget) {
        Objects.requireNonNull(declaredTarget, "declaredTarget");
        return enabled
                && TypeReference.JavaLangObject.getName().equals(
                declaredTarget.getDeclaringClass().getName())
                && protectedSelector(declaredTarget.getSelector());
    }

    /**
     * @param target resolved CHA candidate
     * @return whether the candidate remains in filtered dispatch
     */
    boolean retains(final IMethod target) {
        Objects.requireNonNull(target, "target");
        final IClass declaringClass = target.getDeclaringClass();
        if (TypeReference.JavaLangObject.equals(
                declaringClass.getReference())
                && protectedSelector(target.getSelector())) {
            return true;
        }
        if (declaringClass instanceof SyntheticClass
                || declaringClass.isSynthetic()
                || target.isWalaSynthetic()) {
            return true;
        }
        final String owner = normalizeOwner(
                declaringClass.getName().toString());
        final ClassOwnership winner = ownership.ownershipOf(owner);
        if (winner == null) {
            final ClassLoaderReference loader = declaringClass
                    .getClassLoader().getReference();
            return !ClassLoaderReference.Primordial.equals(loader)
                    && !ClassLoaderReference.Extension.equals(loader);
        }
        return switch (winner.getOrigin()) {
            case PROJECT, REACTOR_DEPENDENCY, SYNTHETIC -> true;
            case JDK -> false;
            case DEPENDENCY -> winner.getSource().artifact()
                    .map(artifact -> diffRelatedExternalMethods.contains(
                            new ExternalMethodKey(artifact, owner,
                                    target.getName().toString(),
                                    target.getDescriptor().toString())))
                    .orElse(false);
        };
    }

    private static boolean targetMethodSurvives(
            final ChangePointKind kind) {
        return kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.METHOD_BODY_CHANGED
                || kind == ChangePointKind.METHOD_ACCESS_NARROWED;
    }

    private static boolean protectedSelector(
            final String name, final String descriptor) {
        return name != null && descriptor != null
                && protectedSelector(Selector.make(name + descriptor));
    }

    private static boolean protectedSelector(final Selector selector) {
        return TO_STRING.equals(selector) || HASH_CODE.equals(selector);
    }

    private static String normalizeOwner(final String owner) {
        return owner.startsWith("L") ? owner.substring(1) : owner;
    }

    /**
     * Exact external target identity including classpath winner artifact.
     *
     * @param artifact winner artifact
     * @param owner internal owner name
     * @param name method name
     * @param descriptor target method descriptor
     */
    private record ExternalMethodKey(
            ArtifactCoord artifact,
            String owner,
            String name,
            String descriptor) {

        ExternalMethodKey {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
