package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.ClassFactoryContextSelector;
import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.DelegatingContext;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjContextSelector;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.EmptyIntSet;
import com.ibm.wala.util.intset.IntSet;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Verifies ClassFactory-compatible k-object Context composition. */
class KObjContextSelectorTest {

    /** Non-ClassFactory static method. */
    private static final MethodReference ORDINARY_METHOD =
            MethodReference.findOrCreate(
                    TypeReference.JavaLangObject,
                    Selector.make("synthetic()V"));

    @Test
    void rejectsInvalidDepth() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new KObjContextSelector(0, fixed(
                        Everywhere.EVERYWHERE, new AtomicInteger())))
                .withMessage("depth must be positive");
    }

    @Test
    void rejectsNullBase() {
        assertThatNullPointerException().isThrownBy(() ->
                new KObjContextSelector(1, null))
                .withMessage("base");
    }

    @Test
    void usesSingleClassFactoryContextBeforeKObjectContext() {
        final Context classFactory =
                new JavaTypeContext(TypeAbstraction.TOP);
        final Context kObject = conflictingContext();
        final AtomicInteger calls = new AtomicInteger();
        final KObjContextSelector selector = new KObjContextSelector(
                1, fixed(classFactory, calls));

        final Context result = selector.getCalleeTarget(
                caller(kObject), staticSite(
                        ClassFactoryContextSelector.FOR_NAME_REF),
                method(ClassFactoryContextSelector.FOR_NAME_REF),
                new InstanceKey[0]);

        assertThat(calls).hasValue(1);
        assertThat(result).isEqualTo(
                new DelegatingContext(classFactory, kObject));
        assertThat(result.get(ContextKey.RECEIVER))
                .isInstanceOf(TypeAbstraction.class);
        assertThat(result.get(
                nObjContextSelector.ALLOCATION_STRING_KEY))
                .isSameAs(Sentinel.ALLOCATION_STRING);
        assertThat(KObjContextGraphInspector.javaTypeContextCount(result))
                .isOne();
    }

    @Test
    void keepsKObjectPrecedenceForOrdinaryCalls() {
        final Context base = new JavaTypeContext(TypeAbstraction.TOP);
        final Context kObject = conflictingContext();
        final KObjContextSelector selector = new KObjContextSelector(
                1, fixed(base, new AtomicInteger()));

        final Context result = selector.getCalleeTarget(
                caller(kObject), staticSite(ORDINARY_METHOD),
                method(ORDINARY_METHOD), new InstanceKey[0]);

        assertThat(result).isEqualTo(
                new DelegatingContext(kObject, base));
        assertThat(result.get(ContextKey.RECEIVER))
                .isSameAs(Sentinel.K_OBJECT_RECEIVER);
    }

    @Test
    void preservesNullBaseBehavior() {
        final KObjContextSelector selector = new KObjContextSelector(
                1, fixed(null, new AtomicInteger()));

        assertThatIllegalArgumentException().isThrownBy(() ->
                selector.getCalleeTarget(
                        caller(conflictingContext()),
                        staticSite(
                                ClassFactoryContextSelector.FOR_NAME_REF),
                        method(ClassFactoryContextSelector.FOR_NAME_REF),
                        new InstanceKey[0]))
                .withMessage("null B");
    }

    @Test
    void keepsWalaRelevantParameterContract() {
        final KObjContextSelector selector = new KObjContextSelector(
                1, fixed(Everywhere.EVERYWHERE, new AtomicInteger()));

        final IntSet staticParameters = selector.getRelevantParameters(
                null, staticSite(ORDINARY_METHOD));
        final IntSet dispatchParameters = selector.getRelevantParameters(
                null, dispatchSite(ORDINARY_METHOD));

        assertThat(staticParameters.isEmpty()).isTrue();
        assertThat(dispatchParameters.size()).isOne();
        assertThat(dispatchParameters.contains(0)).isTrue();
    }

    private Context conflictingContext() {
        return key -> {
            if (ContextKey.RECEIVER.equals(key)) {
                return Sentinel.K_OBJECT_RECEIVER;
            }
            if (nObjContextSelector.ALLOCATION_STRING_KEY.equals(key)) {
                return Sentinel.ALLOCATION_STRING;
            }
            return null;
        };
    }

    private ContextSelector fixed(
            final Context context,
            final AtomicInteger calls) {
        return new ContextSelector() {
            @Override
            public Context getCalleeTarget(
                    final CGNode caller,
                    final CallSiteReference site,
                    final IMethod callee,
                    final InstanceKey[] actualParameters) {
                calls.incrementAndGet();
                return context;
            }

            @Override
            public IntSet getRelevantParameters(
                    final CGNode caller,
                    final CallSiteReference site) {
                return EmptyIntSet.instance;
            }
        };
    }

    private CallSiteReference staticSite(final MethodReference reference) {
        return CallSiteReference.make(
                0, reference, IInvokeInstruction.Dispatch.STATIC);
    }

    private CallSiteReference dispatchSite(final MethodReference reference) {
        return CallSiteReference.make(
                0, reference, IInvokeInstruction.Dispatch.VIRTUAL);
    }

    private CGNode caller(final Context context) {
        return (CGNode) Proxy.newProxyInstance(
                CGNode.class.getClassLoader(),
                new Class<?>[]{CGNode.class},
                (proxy, invoked, arguments) -> {
                    if ("getContext".equals(invoked.getName())) {
                        return context;
                    }
                    throw new UnsupportedOperationException(
                            invoked.getName());
                });
    }

    private IMethod method(final MethodReference reference) {
        return (IMethod) Proxy.newProxyInstance(
                IMethod.class.getClassLoader(),
                new Class<?>[]{IMethod.class},
                (proxy, invoked, arguments) -> {
                    if ("getReference".equals(invoked.getName())) {
                        return reference;
                    }
                    throw new UnsupportedOperationException(
                            invoked.getName());
                });
    }

    /** Synthetic conflicting Context values. */
    private enum Sentinel implements ContextItem {
        /** k-object receiver. */
        K_OBJECT_RECEIVER,

        /** k-object allocation-string value. */
        ALLOCATION_STRING
    }
}

/** Test-only structural inspection of WALA 1.8.0 Context composition. */
final class KObjContextGraphInspector {

    /** First DelegatingContext branch. */
    private static final Field FIRST = field("A");

    /** Fallback DelegatingContext branch. */
    private static final Field SECOND = field("B");

    private KObjContextGraphInspector() {
    }

    static int javaTypeContextCount(final Context context) {
        if (context == null) {
            return 0;
        }
        int count = context instanceof JavaTypeContext ? 1 : 0;
        if (context instanceof DelegatingContext) {
            count += javaTypeContextCount(read(FIRST, context));
            count += javaTypeContextCount(read(SECOND, context));
        }
        return count;
    }

    private static Field field(final String name) {
        try {
            final Field result = DelegatingContext.class
                    .getDeclaredField(name);
            if (!result.trySetAccessible()) {
                throw new IllegalStateException(
                        "Cannot inspect DelegatingContext." + name);
            }
            return result;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(
                    "WALA DelegatingContext shape changed", exception);
        }
    }

    private static Context read(
            final Field field,
            final Context context) {
        try {
            return (Context) field.get(context);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Cannot read WALA DelegatingContext", exception);
        }
    }
}
