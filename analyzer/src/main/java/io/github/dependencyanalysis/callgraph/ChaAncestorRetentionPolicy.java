package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.DependencyMethodBodyPolicy;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/features/call-graph-engine.md - CHA external ancestor exception
/** Immutable set of unselected external ancestors whose bodies remain real. */
final class ChaAncestorRetentionPolicy {

    /** Normalized retained external binary names. */
    private final Set<String> retainedTypes;

    private ChaAncestorRetentionPolicy(final Set<String> types) {
        retainedTypes = Set.copyOf(types);
    }

    /**
     * Computes the transitive superclass and direct-interface closure.
     *
     * @param unit module analysis input
     * @param ownership classpath winner ownership
     * @param hierarchy resolved WALA class hierarchy
     * @param enabled whether CHA changed-path pruning is active
     * @return immutable ancestor exception
     */
    static ChaAncestorRetentionPolicy create(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership,
            final IClassHierarchy hierarchy,
            final boolean enabled) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(hierarchy, "hierarchy");
        if (!enabled || unit.getChangedPathSelection().actualMode()
                != DependencyAnalysisScopeMode.CHANGED_PATHS) {
            return disabled();
        }
        final Set<String> retained = new LinkedHashSet<>();
        final Set<String> visited = new LinkedHashSet<>();
        final Deque<IClass> work = new ArrayDeque<>();
        for (IClass type : hierarchy) {
            if (seed(type, unit, ownership)) {
                enqueueParents(type, work);
            }
        }
        while (!work.isEmpty()) {
            final IClass type = work.removeFirst();
            final String name = normalize(type.getName().toString());
            if (!visited.add(name) || excluded(type, ownership)) {
                continue;
            }
            final ClassOwnership owner = ownership.ownershipOf(name);
            if (owner != null && owner.getOrigin() == CodeOrigin.DEPENDENCY
                    && owner.getSource().artifact()
                    .map(unit.getChangedPathSelection()::policyFor)
                    .orElse(DependencyMethodBodyPolicy.REAL_IR)
                    == DependencyMethodBodyPolicy.NO_OP) {
                retained.add(name);
            }
            enqueueParents(type, work);
        }
        return new ChaAncestorRetentionPolicy(retained);
    }

    /** @return disabled empty policy */
    static ChaAncestorRetentionPolicy disabled() {
        return new ChaAncestorRetentionPolicy(Set.of());
    }

    /** @return number of retained external ancestor types */
    int retainedTypeCount() {
        return retainedTypes.size();
    }

    /**
     * @param method resolved method
     * @return whether the method's declaring type is retained
     */
    boolean retains(final IMethod method) {
        return method != null && retains(method.getDeclaringClass());
    }

    /**
     * @param type resolved declaring type
     * @return whether the resolved type is retained
     */
    boolean retains(final IClass type) {
        return type != null && retainedTypes.contains(
                normalize(type.getName().toString()));
    }

    private static boolean seed(
            final IClass type,
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership) {
        if (excluded(type, ownership)) {
            return false;
        }
        final ClassOwnership owner = ownership.ownershipOf(
                type.getName().toString());
        if (owner == null) {
            return false;
        }
        return switch (owner.getOrigin()) {
            case PROJECT, REACTOR_DEPENDENCY -> true;
            case DEPENDENCY -> owner.getSource().artifact()
                    .map(unit.getChangedPathSelection()::policyFor)
                    .orElse(DependencyMethodBodyPolicy.NO_OP)
                    == DependencyMethodBodyPolicy.REAL_IR;
            case JDK, SYNTHETIC -> false;
        };
    }

    private static boolean excluded(
            final IClass type,
            final ClassOwnershipIndex ownership) {
        if (type instanceof SyntheticClass || type.isSynthetic()) {
            return true;
        }
        final ClassOwnership owner = ownership.ownershipOf(
                type.getName().toString());
        if (owner != null) {
            return owner.getOrigin() == CodeOrigin.JDK
                    || owner.getOrigin() == CodeOrigin.SYNTHETIC;
        }
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader);
    }

    private static void enqueueParents(
            final IClass type,
            final Deque<IClass> work) {
        final IClass superclass = type.getSuperclass();
        if (superclass != null) {
            work.addLast(superclass);
        }
        type.getDirectInterfaces().stream()
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(value ->
                        value.getName().toString()))
                .forEach(work::addLast);
    }

    private static String normalize(final String name) {
        return name.startsWith("L") ? name.substring(1) : name;
    }
}
