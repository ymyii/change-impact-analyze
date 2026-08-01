package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import java.util.Comparator;

/** Entrypoint whose reference parameters enumerate all concrete subtypes. */
public final class DeterministicSubtypesEntrypoint
        extends DefaultEntrypoint {

    /** Owning class hierarchy. */
    private final IClassHierarchy hierarchy;

    /**
     * Creates a deterministic all-subtypes entrypoint.
     *
     * @param method project method
     * @param cha module class hierarchy
     */
    public DeterministicSubtypesEntrypoint(
            final IMethod method, final IClassHierarchy cha) {
        super(method, cha);
        hierarchy = cha;
    }

    @Override
    protected TypeReference[] makeParameterTypes(
            final IMethod method, final int index) {
        final TypeReference declared = method.getParameterType(index);
        if (method.isInit() && index == 0) {
            return new TypeReference[]{declared};
        }
        if (declared.isPrimitiveType()) {
            return new TypeReference[]{declared};
        }
        final IClass declaredClass = hierarchy.lookupClass(declared);
        if (declaredClass == null) {
            return new TypeReference[]{declared};
        }
        final TypeReference[] candidates = hierarchy
                .computeSubClasses(declared)
                .stream()
                .filter(candidate -> !candidate.isAbstract())
                .filter(candidate -> !candidate.isInterface())
                .filter(candidate -> hierarchy.isAssignableFrom(
                        declaredClass, candidate))
                .map(IClass::getReference)
                .sorted(Comparator.comparing(reference ->
                        reference.getName().toString()))
                .toArray(TypeReference[]::new);
        return candidates.length == 0
                ? new TypeReference[]{declared} : candidates;
    }
}
