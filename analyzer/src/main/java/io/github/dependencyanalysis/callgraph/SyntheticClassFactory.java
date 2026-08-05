package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;

/** Registers a synthetic model class in the active WALA hierarchy. */
@FunctionalInterface
public interface SyntheticClassFactory {

    /**
     * Registers one newly created class.
     *
     * @param syntheticClass synthetic class
     */
    void register(IClass syntheticClass);
}
