package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Truth-table tests for Java 8 target-side JVM access checks. */
class JvmAccessCheckerTest {

    /** Typed hierarchy fixture. */
    private final HierarchyFixture fixture = new HierarchyFixture();

    /** Checker under test. */
    private final JvmAccessChecker checker =
            new JvmAccessChecker(fixture.hierarchy());

    @Test
    void sameRuntimePackageRequiresBothPackageAndLoaderIdentity() {
        final IClass caller = fixture.type("shared/pkg/Caller",
                ClassLoaderReference.Application);
        final IClass sameLoader = fixture.type("shared/pkg/Declaration",
                ClassLoaderReference.Application);
        final IClass differentLoader = fixture.type(
                "shared/pkg/ExtensionDeclaration",
                ClassLoaderReference.Extension);

        assertDecision(request(caller, sameLoader, sameLoader,
                        JvmAccess.PACKAGE_PRIVATE,
                        JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.SAME_RUNTIME_PACKAGE);
        assertDecision(request(caller, sameLoader, sameLoader,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_INSTANCE,
                        ReceiverType.unknown()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.SAME_RUNTIME_PACKAGE);
        assertDecision(request(caller, differentLoader, differentLoader,
                        JvmAccess.PACKAGE_PRIVATE,
                        JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.DIFFERENT_RUNTIME_PACKAGE);
    }

    @Test
    void publicPackageAndPrivateAccessUseDeclarationBoundary() {
        final IClass caller = fixture.applicationType("consumer/Caller");
        final IClass declaration = fixture.applicationType("api/Api");
        final IClass packagePeer = fixture.applicationType("api/Peer");

        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PUBLIC, JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PUBLIC_ACCESS);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PACKAGE_PRIVATE,
                        JvmReferenceKind.CLASS,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.DIFFERENT_RUNTIME_PACKAGE);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PRIVATE,
                        JvmReferenceKind.FIELD_STATIC,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.PRIVATE_MEMBER);
        assertDecision(request(packagePeer, declaration, declaration,
                        JvmAccess.PRIVATE,
                        JvmReferenceKind.FIELD_STATIC,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.PRIVATE_MEMBER);
        assertDecision(request(declaration, declaration, declaration,
                        JvmAccess.PRIVATE,
                        JvmReferenceKind.METHOD_INSTANCE,
                        ReceiverType.thisReceiver()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.DECLARING_CLASS);
    }

    @Test
    void protectedCrossPackageCallerMustBeSubclass() {
        final IClass caller = fixture.applicationType("consumer/Caller");
        final IClass declaration = fixture.applicationType("api/Base");

        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_STATIC,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.PROTECTED_CALLER_NOT_SUBCLASS);
    }

    @Test
    void protectedSubclassAllowsStaticThisAndSuperReferences() {
        final IClass declaration = fixture.applicationType("api/Base");
        final IClass caller = fixture.applicationType("consumer/Sub");
        fixture.extendsType(caller, declaration);

        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_STATIC,
                        ReceiverType.notApplicable()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PROTECTED_STATIC_SUBCLASS);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.FIELD_INSTANCE,
                        ReceiverType.thisReceiver()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PROTECTED_THIS_RECEIVER);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_INSTANCE,
                        ReceiverType.superReceiver()),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PROTECTED_SUPER_RECEIVER);
    }

    @Test
    void protectedPointReceiverMustBeAssignableToCaller() {
        final IClass declaration = fixture.applicationType("api/Base");
        final IClass caller = fixture.applicationType("consumer/Sub");
        final IClass child = fixture.applicationType("consumer/Child");
        final IClass sibling = fixture.applicationType("consumer/Sibling");
        fixture.extendsType(caller, declaration);
        fixture.extendsType(child, caller);
        fixture.extendsType(sibling, declaration);

        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_INSTANCE,
                        ReceiverType.point(child)),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PROTECTED_RECEIVER_ASSIGNABLE);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.CONSTRUCTOR,
                        ReceiverType.point(sibling)),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.PROTECTED_RECEIVER_NOT_ASSIGNABLE);
    }

    @Test
    void protectedConeAndUnknownReceiverRemainPotentialWhenUnproven() {
        final IClass declaration = fixture.applicationType("api/Base");
        final IClass caller = fixture.applicationType("consumer/Sub");
        fixture.extendsType(caller, declaration);

        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.FIELD_INSTANCE,
                        ReceiverType.cone(caller)),
                AccessDecision.ACCESSIBLE,
                AccessDecisionReason.PROTECTED_RECEIVER_ASSIGNABLE);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.FIELD_INSTANCE,
                        ReceiverType.cone(declaration)),
                AccessDecision.POTENTIALLY_INACCESSIBLE,
                AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN);
        assertDecision(request(caller, declaration, declaration,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_INSTANCE,
                        ReceiverType.unknown()),
                AccessDecision.POTENTIALLY_INACCESSIBLE,
                AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN);
    }

    @Test
    void protectedSymbolicOwnerMustBelongToRelevantHierarchy() {
        final IClass declaration = fixture.applicationType("api/Base");
        final IClass caller = fixture.applicationType("consumer/Sub");
        final IClass unrelatedOwner = fixture.applicationType(
                "other/Unrelated");
        fixture.extendsType(caller, declaration);

        assertDecision(request(caller, declaration, unrelatedOwner,
                        JvmAccess.PROTECTED,
                        JvmReferenceKind.METHOD_STATIC,
                        ReceiverType.notApplicable()),
                AccessDecision.INACCESSIBLE,
                AccessDecisionReason.PROTECTED_SYMBOLIC_OWNER);
    }

    @Test
    void typedAccessEvidenceKeepsDecisionAndReferenceIdentity() {
        final AccessReferenceEvidence evidence =
                new AccessReferenceEvidence(
                        new AccessTransition(
                                JvmAccess.PUBLIC, JvmAccess.PROTECTED),
                        AccessDecision.POTENTIALLY_INACCESSIBLE,
                        AccessDecisionReason.PROTECTED_RECEIVER_UNKNOWN,
                        "consumer/Sub.call()V", "api/Base", "api/Base",
                        "UNKNOWN", "api/Base.call()V");

        assertThat(evidence.stableKey()).isEqualTo(
                "PUBLIC->PROTECTED|POTENTIALLY_INACCESSIBLE|"
                        + "PROTECTED_RECEIVER_UNKNOWN|"
                        + "consumer/Sub.call()V|api/Base|api/Base|"
                        + "UNKNOWN|api/Base.call()V");
        assertThat(evidence.render())
                .startsWith("Potential access incompatibility:")
                .contains("access=PUBLIC->PROTECTED")
                .contains("decision=POTENTIALLY_INACCESSIBLE")
                .contains("reference=api/Base.call()V");
    }

    private AccessCheckRequest request(
            final IClass caller,
            final IClass declaration,
            final IClass symbolicOwner,
            final JvmAccess access,
            final JvmReferenceKind kind,
            final ReceiverType receiver) {
        return new AccessCheckRequest(caller, declaration, symbolicOwner,
                access, kind, receiver);
    }

    private void assertDecision(
            final AccessCheckRequest request,
            final AccessDecision decision,
            final AccessDecisionReason reason) {
        assertThat(checker.check(request))
                .isEqualTo(new AccessCheckResult(decision, reason));
    }

    /** Minimal typed WALA relation fixture used only by access policy tests. */
    private static final class HierarchyFixture {

        /** Direct superclass by class identity. */
        private final Map<IClass, IClass> parents =
                new IdentityHashMap<>();

        /** Checker-facing hierarchy double. */
        private final IClassHierarchy hierarchy = proxy(
                IClassHierarchy.class, (value, method, arguments) -> {
                    if ("isSubclassOf".equals(method.getName())) {
                        return isSubclassOf((IClass) arguments[0],
                                (IClass) arguments[1]);
                    }
                    if ("isAssignableFrom".equals(method.getName())) {
                        final IClass target = (IClass) arguments[0];
                        final IClass valueType = (IClass) arguments[1];
                        return target == valueType
                                || isSubclassOf(valueType, target);
                    }
                    return objectMethod(value, method.getName(), arguments,
                            "HierarchyFixture");
                });

        IClassHierarchy hierarchy() {
            return hierarchy;
        }

        IClass applicationType(final String name) {
            return type(name, ClassLoaderReference.Application);
        }

        IClass type(
                final String name,
                final ClassLoaderReference loaderReference) {
            final IClassLoader loader = proxy(
                    IClassLoader.class,
                    (value, method, arguments) -> {
                        if ("getReference".equals(method.getName())) {
                            return loaderReference;
                        }
                        return objectMethod(value, method.getName(),
                                arguments, loaderReference.toString());
                    });
            final TypeName typeName = TypeName.findOrCreate("L" + name);
            final TypeReference reference = TypeReference.findOrCreate(
                    loaderReference, typeName);
            return proxy(IClass.class,
                    (value, method, arguments) -> switch (method.getName()) {
                        case "getClassLoader" -> loader;
                        case "getName" -> typeName;
                        case "getReference" -> reference;
                        default -> objectMethod(value, method.getName(),
                                arguments, reference.toString());
                    });
        }

        void extendsType(final IClass child, final IClass parent) {
            parents.put(child, parent);
        }

        private boolean isSubclassOf(
                final IClass candidate,
                final IClass expectedParent) {
            IClass current = parents.get(candidate);
            while (current != null) {
                if (current == expectedParent) {
                    return true;
                }
                current = parents.get(current);
            }
            return false;
        }

        private static Object objectMethod(
                final Object value,
                final String name,
                final Object[] arguments,
                final String description) {
            return switch (name) {
                case "equals" -> value == arguments[0];
                case "hashCode" -> System.identityHashCode(value);
                case "toString" -> description;
                default -> throw new UnsupportedOperationException(name);
            };
        }

        private static <T> T proxy(
                final Class<T> type,
                final java.lang.reflect.InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(
                    type.getClassLoader(), new Class<?>[]{type}, handler));
        }
    }
}
