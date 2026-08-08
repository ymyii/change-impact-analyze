package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;

import java.util.Objects;

/**
 * Validated ServiceLoader provider fact.
 *
 * @param type provider type
 * @param constructor public zero-argument constructor
 */
record ServiceLoaderProviderDefinition(
        IClass type,
        IMethod constructor) {

    ServiceLoaderProviderDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(constructor, "constructor");
    }
}
