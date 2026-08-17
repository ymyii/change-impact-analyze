package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphInput;
import io.github.dependencyanalysis.callgraph.scope.CallGraphChange;
import io.github.dependencyanalysis.callgraph.scope.CallGraphChangeKind;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyScope;
import io.github.dependencyanalysis.callgraph.scope.DependencyBodyPolicy;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable Diff-directed target-retention policy for CHA Object dispatch. */
public final class ChaDispatchTargetPolicy {

    /** Maximum stable JDK-dispatch examples. */
    private static final int EXAMPLE_LIMIT = 10;

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
    private final CallGraphDependencyScope selection;

    /** Type-level external ancestor exception. */
    private final ChaAncestorRetentionPolicy ancestorRetention;

    /** Stable identities of external method targets removed from CHA. */
    private final Set<String> prunedExternalTargets = new LinkedHashSet<>();

    /** Whether to format bounded JDK-dispatch examples. */
    private final boolean captureJdkExamples;

    /** Distinct targets removed by the fixed JDK-declared dispatch rule. */
    private final Set<String> prunedJdkDeclaredTargets = new LinkedHashSet<>();

    /** Deterministic smallest JDK-declared dispatch examples. */
    private final TreeMap<String,
            JdkDeclaredDispatchPruningSummary.TargetExample> jdkExamples =
            new TreeMap<>();

    private ChaDispatchTargetPolicy(
            final boolean filterEnabled,
            final boolean pruneExternal,
            final Set<ExternalMethodKey> externalMethods,
            final ClassOwnershipIndex winnerOwnership,
            final CallGraphDependencyScope bodySelection,
            final ChaAncestorRetentionPolicy ancestors,
            final boolean captureExamples) {
        enabled = filterEnabled;
        externalPruning = pruneExternal;
        diffRelatedExternalMethods = Set.copyOf(Objects.requireNonNull(
                externalMethods, "externalMethods"));
        ownership = Objects.requireNonNull(
                winnerOwnership, "winnerOwnership");
        selection = bodySelection;
        ancestorRetention = Objects.requireNonNull(ancestors, "ancestors");
        captureJdkExamples = captureExamples;
    }

    /**
     * Builds a policy from target-side module Diff and winner ownership.
     *
     * @param input module Call Graph input
     * @param ownership target classpath winner ownership
     * @return immutable enabled policy
     */
    public static ChaDispatchTargetPolicy create(
            final ModuleCallGraphInput input,
            final ClassOwnershipIndex ownership) {
        return create(input, ownership,
                ChaAncestorRetentionPolicy.disabled(), false, false);
    }

    /**
     * Builds the full dispatch policy.
     *
     * @param input module Call Graph input
     * @param ownership target classpath winner ownership
     * @param ancestors retained ancestor policy
     * @param pruneExternal whether to prune unrelated external targets
     * @return immutable dispatch policy
     */
    public static ChaDispatchTargetPolicy create(
            final ModuleCallGraphInput input,
            final ClassOwnershipIndex ownership,
            final ChaAncestorRetentionPolicy ancestors,
            final boolean pruneExternal) {
        return create(input, ownership, ancestors, pruneExternal, false);
    }

    /**
     * Builds the full dispatch policy with optional bounded examples.
     *
     * @param input module Call Graph input
     * @param ownership target classpath winner ownership
     * @param ancestors retained ancestor policy
     * @param pruneExternal whether to prune unrelated external targets
     * @param captureExamples whether to format bounded examples
     * @return immutable dispatch policy
     */
    public static ChaDispatchTargetPolicy create(
            final ModuleCallGraphInput input,
            final ClassOwnershipIndex ownership,
            final ChaAncestorRetentionPolicy ancestors,
            final boolean pruneExternal,
            final boolean captureExamples) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(ownership, "ownership");
        final Set<ExternalMethodKey> methods = new LinkedHashSet<>();
        for (CallGraphChange point : input.changes()) {
            if (!targetMethodSurvives(point.kind())
                    || !protectedSelector(point.name(), point.descriptor())) {
                continue;
            }
            final ClassOwnership winner = ownership.ownershipOf(
                    point.owner());
            if (winner == null
                    || winner.getOrigin() != CodeOrigin.DEPENDENCY
                    || winner.getSource().artifact().isEmpty()
                    || !winner.getSource().artifact().get().equals(
                    point.artifact())) {
                continue;
            }
            methods.add(new ExternalMethodKey(point.artifact(),
                    normalizeOwner(point.owner()), point.name(),
                    point.descriptor()));
        }
        return new ChaDispatchTargetPolicy(true, pruneExternal, methods,
                ownership, input.dependencyScope(), ancestors,
                captureExamples);
    }

    /** @return compatibility policy that leaves all dispatch unchanged */
    public static ChaDispatchTargetPolicy disabled() {
        return new ChaDispatchTargetPolicy(false, false, Set.of(),
                new ClassOwnershipIndex(), null,
                ChaAncestorRetentionPolicy.disabled(), false);
    }

    /**
     * @param declaredTarget declared virtual target
     * @return whether this exact Object dispatch is filtered
     */
    public boolean filters(final MethodReference declaredTarget) {
        Objects.requireNonNull(declaredTarget, "declaredTarget");
        return enabled && (externalPruning
                || TypeReference.JavaLangObject.getName().equals(
                declaredTarget.getDeclaringClass().getName())
                && protectedSelector(declaredTarget.getSelector()));
    }

    /**
     * @param declaredTarget declared virtual target
     * @return whether a CHA dispatch target set requires filtering
     */
    public boolean filtersDispatch(final MethodReference declaredTarget) {
        return enabled && (filters(declaredTarget)
                || jdkDeclared(declaredTarget));
    }

    /**
     * Applies the fixed JDK-declared boundary only to CHA dispatch target sets.
     *
     * @param declaredTarget declared virtual target
     * @param target resolved CHA candidate
     * @return whether the candidate remains in filtered dispatch
     */
    public boolean retainsDispatchTarget(
            final MethodReference declaredTarget,
            final IMethod target) {
        if (enabled && jdkDeclared(declaredTarget)) {
            final CodeOrigin origin = origin(target.getDeclaringClass());
            if (origin != CodeOrigin.JDK || target.isAbstract()) {
                recordJdkPruned(declaredTarget, target, origin,
                        target.isAbstract() ? "non-concrete-target"
                                : "non-jdk-target");
                return false;
            }
        }
        return !filters(declaredTarget) || retains(declaredTarget, target);
    }

    /**
     * @param declaredTarget declared call target
     * @param target resolved CHA candidate
     * @return whether the candidate remains in filtered dispatch
     */
    public boolean retains(
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
    public int prunedExternalMethodTargetCount() {
        return prunedExternalTargets.size();
    }

    /** @return fixed JDK-declared dispatch pruning evidence */
    public JdkDeclaredDispatchPruningSummary jdkDeclaredDispatchSummary() {
        synchronized (prunedJdkDeclaredTargets) {
            return new JdkDeclaredDispatchPruningSummary(
                    prunedJdkDeclaredTargets.size(),
                    List.copyOf(jdkExamples.values()));
        }
    }

    private boolean jdkDeclared(final MethodReference declaredTarget) {
        final ClassLoaderReference loader = declaredTarget
                .getDeclaringClass().getClassLoader();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader);
    }

    private CodeOrigin origin(final IClass type) {
        final ClassOwnership winner = ownership.ownershipOf(
                type.getName().toString());
        if (winner != null) {
            return winner.getOrigin();
        }
        if (type instanceof SyntheticClass || type.isSynthetic()) {
            return CodeOrigin.SYNTHETIC;
        }
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader)
                ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
    }

    private void recordJdkPruned(
            final MethodReference declaredTarget,
            final IMethod target,
            final CodeOrigin origin,
            final String reason) {
        final String targetIdentity = target.getReference().toString();
        synchronized (prunedJdkDeclaredTargets) {
            prunedJdkDeclaredTargets.add(
                    declaredTarget + "|" + targetIdentity);
            if (!captureJdkExamples) {
                return;
            }
            final JdkDeclaredDispatchPruningSummary.TargetExample example =
                    new JdkDeclaredDispatchPruningSummary.TargetExample(
                            declaredTarget.toString(), targetIdentity,
                            origin.name(), reason);
            jdkExamples.put(example.stableKey(), example);
            while (jdkExamples.size() > EXAMPLE_LIMIT) {
                jdkExamples.pollLastEntry();
            }
        }
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
                .orElse(DependencyBodyPolicy.REAL_IR)
                == DependencyBodyPolicy.REAL_IR;
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
            final CallGraphChangeKind kind) {
        return kind == CallGraphChangeKind.METHOD_ADDED
                || kind == CallGraphChangeKind.METHOD_BODY_CHANGED
                || kind == CallGraphChangeKind.METHOD_ACCESS_NARROWED;
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
