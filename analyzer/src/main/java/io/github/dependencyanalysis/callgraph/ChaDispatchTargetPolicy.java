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
import io.github.dependencyanalysis.impact.DependencyMethodBodyPolicy;
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

    /** Whether all unselected external targets are pruned. */
    private final boolean externalPruning;

    /** Target external methods whose override Diff survives in target. */
    private final Set<ExternalMethodKey> diffRelatedExternalMethods;

    /** Target classpath winner ownership, frozen before strategy build. */
    private final ClassOwnershipIndex ownership;

    /** Artifact-level method-body selection. */
    private final io.github.dependencyanalysis.impact
            .ModuleChangedPathSelection selection;

    /** Type-level external ancestor exception. */
    private final ChaAncestorRetentionPolicy ancestorRetention;

    /** Stable identities of external method targets removed from CHA. */
    private final Set<String> prunedExternalTargets = new LinkedHashSet<>();

    private ChaDispatchTargetPolicy(
            final boolean filterEnabled,
            final boolean pruneExternal,
            final Set<ExternalMethodKey> externalMethods,
            final ClassOwnershipIndex winnerOwnership,
            final io.github.dependencyanalysis.impact
                    .ModuleChangedPathSelection bodySelection,
            final ChaAncestorRetentionPolicy ancestors) {
        enabled = filterEnabled;
        externalPruning = pruneExternal;
        diffRelatedExternalMethods = Set.copyOf(Objects.requireNonNull(
                externalMethods, "externalMethods"));
        ownership = Objects.requireNonNull(
                winnerOwnership, "winnerOwnership");
        selection = bodySelection;
        ancestorRetention = Objects.requireNonNull(ancestors, "ancestors");
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
        return create(unit, ownership,
                ChaAncestorRetentionPolicy.disabled(), false);
    }

    static ChaDispatchTargetPolicy create(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership,
            final ChaAncestorRetentionPolicy ancestors,
            final boolean pruneExternal) {
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
        return new ChaDispatchTargetPolicy(true, pruneExternal, methods,
                ownership, unit.getChangedPathSelection(), ancestors);
    }

    /** @return compatibility policy that leaves all dispatch unchanged */
    static ChaDispatchTargetPolicy disabled() {
        return new ChaDispatchTargetPolicy(false, false, Set.of(),
                new ClassOwnershipIndex(), null,
                ChaAncestorRetentionPolicy.disabled());
    }

    /**
     * @param declaredTarget declared virtual target
     * @return whether this exact Object dispatch is filtered
     */
    boolean filters(final MethodReference declaredTarget) {
        Objects.requireNonNull(declaredTarget, "declaredTarget");
        return enabled && (externalPruning
                || TypeReference.JavaLangObject.getName().equals(
                declaredTarget.getDeclaringClass().getName())
                && protectedSelector(declaredTarget.getSelector()));
    }

    /**
     * @param declaredTarget declared call target
     * @param target resolved CHA candidate
     * @return whether the candidate remains in filtered dispatch
     */
    boolean retains(
            final MethodReference declaredTarget,
            final IMethod target) {
        Objects.requireNonNull(declaredTarget, "declaredTarget");
        Objects.requireNonNull(target, "target");
        if (objectDispatch(declaredTarget)
                && !retainsObjectDispatchTarget(target)) {
            recordPrunedExternal(target);
            return false;
        }
        if (!externalPruning || retainsExternalTarget(target)) {
            return true;
        }
        recordPrunedExternal(target);
        return false;
    }

    /** @return stable number of distinct pruned external method targets */
    int prunedExternalMethodTargetCount() {
        return prunedExternalTargets.size();
    }

    private boolean objectDispatch(final MethodReference declaredTarget) {
        return TypeReference.JavaLangObject.getName().equals(
                declaredTarget.getDeclaringClass().getName())
                && protectedSelector(declaredTarget.getSelector());
    }

    private boolean retainsObjectDispatchTarget(final IMethod target) {
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

    private boolean retainsExternalTarget(final IMethod target) {
        final IClass declaringClass = target.getDeclaringClass();
        if (declaringClass instanceof SyntheticClass
                || declaringClass.isSynthetic()
                || target.isWalaSynthetic()) {
            return true;
        }
        final ClassOwnership winner = ownership.ownershipOf(
                declaringClass.getName().toString());
        if (winner == null || winner.getOrigin() != CodeOrigin.DEPENDENCY) {
            return true;
        }
        return ancestorRetention.retains(declaringClass)
                || winner.getSource().artifact().map(selection::policyFor)
                .orElse(DependencyMethodBodyPolicy.REAL_IR)
                == DependencyMethodBodyPolicy.REAL_IR;
    }

    private void recordPrunedExternal(final IMethod target) {
        final ClassOwnership winner = ownership.ownershipOf(
                target.getDeclaringClass().getName().toString());
        if (winner != null && winner.getOrigin() == CodeOrigin.DEPENDENCY) {
            prunedExternalTargets.add(target.getReference().toString()
                    + "|" + winner.getSource().stableKey());
        }
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
