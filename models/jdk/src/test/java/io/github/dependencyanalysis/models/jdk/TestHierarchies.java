package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.core.java11.JrtModule;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.IOException;
import java.util.List;

/** Shared host-JDK hierarchy fixtures for the common engine. */
final class TestHierarchies {

    private TestHierarchies() {
    }

    static IClassHierarchy hostJdk() throws IOException,
            ClassHierarchyException {
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        scope.addToScope(ClassLoaderReference.Primordial,
                new JrtModule("java.base"));
        return ClassHierarchyFactory.make(scope);
    }

    static AnalysisOptions options(final IClassHierarchy hierarchy) {
        final AnalysisOptions result = new AnalysisOptions(
                hierarchy.getScope(), List.of());
        Util.addDefaultSelectors(result, hierarchy);
        return result;
    }
}
