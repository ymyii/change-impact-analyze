package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;

/** Entrypoint that models every parameter with one declared-type candidate. */
public final class DeclaredTypesEntrypoint extends DefaultEntrypoint {

    /**
     * Creates a declared-type-only entrypoint.
     *
     * @param method PROJECT method
     * @param hierarchy Module hierarchy
     * @param syntheticTypes abstract/interface placeholder registry
     */
    public DeclaredTypesEntrypoint(
            final IMethod method,
            final IClassHierarchy hierarchy,
            final EntrypointSyntheticTypeRegistry syntheticTypes) {
        super(method, hierarchy);
        Objects.requireNonNull(syntheticTypes, "syntheticTypes");
        for (int index = 0; index < method.getNumberOfParameters(); index++) {
            final TypeReference declared = method.getParameterType(index);
            if (declared.isPrimitiveType() || declared.isArrayType()) {
                continue;
            }
            final IClass type = hierarchy.lookupClass(declared);
            if (type != null && (type.isInterface() || type.isAbstract())) {
                setParameterTypes(index, new TypeReference[]{
                        syntheticTypes.placeholder(type)});
            }
        }
    }
}
