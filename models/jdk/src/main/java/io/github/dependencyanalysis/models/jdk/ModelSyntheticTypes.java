package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClass;
import com.ibm.wala.ipa.summaries.BypassSyntheticClassLoader;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registers model-only concrete placeholders in one Synthetic loader. */
final class ModelSyntheticTypes {

    /** Active hierarchy. */
    private final IClassHierarchy hierarchy;

    /** WALA synthetic loader. */
    private final BypassSyntheticClassLoader loader;

    /** Reused placeholders. */
    private final Map<TypeReference, TypeReference> placeholders =
            new LinkedHashMap<>();

    ModelSyntheticTypes(final IClassHierarchy activeHierarchy) {
        hierarchy = activeHierarchy;
        final IClassLoader value = hierarchy.getLoader(
                hierarchy.getScope().getSyntheticLoader());
        if (!(value instanceof BypassSyntheticClassLoader)) {
            throw new JdkModelException(
                    "WALA Synthetic class loader is unavailable");
        }
        loader = (BypassSyntheticClassLoader) value;
    }

    void register(final IClass type) {
        final TypeName name = type.getName();
        if (loader.lookupClass(name) == null) {
            loader.registerClass(name, type);
        }
    }

    TypeReference concrete(final TypeReference declared) {
        if (declared.isArrayType() || declared.isPrimitiveType()) {
            return declared;
        }
        final IClass type = hierarchy.lookupClass(declared);
        if (type == null || !type.isInterface() && !type.isAbstract()) {
            return declared;
        }
        return placeholders.computeIfAbsent(declared, ignored -> {
            final TypeName name = BypassSyntheticClass.getName(declared);
            final IClass existing = loader.lookupClass(name);
            if (existing != null) {
                return existing.getReference();
            }
            final BypassSyntheticClass synthetic =
                    new BypassSyntheticClass(type, loader, hierarchy);
            loader.registerClass(name, synthetic);
            return synthetic.getReference();
        });
    }
}
