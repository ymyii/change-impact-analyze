package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests deterministic entrypoint parameter initialization. */
class DeterministicSubtypesEntrypointTest {

    @Test
    void usesSuperclassHierarchyDuringVirtualConstructionCall() {
        final IClass concrete = proxy(IClass.class, (method, arguments) ->
                switch (method.getName()) {
                    case "isAbstract", "isInterface" -> false;
                    case "getReference" -> TypeReference.JavaLangString;
                    default -> defaultValue(method.getReturnType());
                });
        final IClassHierarchy hierarchy = proxy(
                IClassHierarchy.class, (method, arguments) ->
                        switch (method.getName()) {
                            case "lookupClass" -> concrete;
                            case "computeSubClasses" -> List.of(concrete);
                            case "isAssignableFrom" -> true;
                            default -> defaultValue(method.getReturnType());
                        });
        final IMethod target = proxy(IMethod.class, (method, arguments) ->
                switch (method.getName()) {
                    case "getNumberOfParameters" -> 1;
                    case "getParameterType" -> TypeReference.JavaLangString;
                    case "isInit" -> false;
                    case "getDeclaringClass" -> concrete;
                    default -> defaultValue(method.getReturnType());
                });

        final DeterministicSubtypesEntrypoint entrypoint =
                new DeterministicSubtypesEntrypoint(target, hierarchy);

        assertThat(entrypoint.getParameterTypes(0))
                .containsExactly(TypeReference.JavaLangString);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(
            final Class<T> type, final Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (value, method, arguments) ->
                        invocation.invoke(method, arguments));
    }

    private Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    /** Test proxy invocation. */
    @FunctionalInterface
    private interface Invocation {

        /**
         * Invokes one proxy method.
         *
         * @param method invoked method
         * @param arguments method arguments
         * @return proxy result
         */
        Object invoke(
                java.lang.reflect.Method method, Object[] arguments);
    }
}
