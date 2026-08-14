package io.github.dependencyanalysis.callgraph.entrypoint;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClass;
import com.ibm.wala.ipa.summaries.BypassSyntheticClassLoader;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-hierarchy synthetic placeholders for abstract entrypoint parameters. */
public final class EntrypointSyntheticTypeRegistry {

    /** Active hierarchy. */
    private final IClassHierarchy hierarchy;

    /** WALA synthetic class loader. */
    private final BypassSyntheticClassLoader loader;

    /** One placeholder per declared type. */
    private final Map<TypeReference, TypeReference> placeholders =
            new LinkedHashMap<>();

    /**
     * Creates a registry for one Module hierarchy.
     *
     * @param cha active hierarchy
     */
    public EntrypointSyntheticTypeRegistry(final IClassHierarchy cha) {
        hierarchy = Objects.requireNonNull(cha, "cha");
        final IClassLoader value = hierarchy.getLoader(
                hierarchy.getScope().getSyntheticLoader());
        if (!(value instanceof BypassSyntheticClassLoader)) {
            throw new CallGraphException(
                    "WALA Synthetic class loader is unavailable");
        }
        loader = (BypassSyntheticClassLoader) value;
    }

    /**
     * Returns one shared concrete placeholder for an interface/abstract type.
     *
     * @param declared resolved declared type
     * @return registered synthetic type reference
     */
    public TypeReference placeholder(final IClass declared) {
        Objects.requireNonNull(declared, "declared");
        return placeholders.computeIfAbsent(declared.getReference(), key -> {
            final TypeName name = BypassSyntheticClass.getName(key);
            final IClass existing = loader.lookupClass(name);
            if (existing != null) {
                return existing.getReference();
            }
            final BypassSyntheticClass synthetic =
                    new BypassSyntheticClass(declared, loader, hierarchy);
            loader.registerClass(name, synthetic);
            return synthetic.getReference();
        });
    }

    /** @return number of registered/reused declared-type placeholders */
    public int size() {
        return placeholders.size();
    }
}
