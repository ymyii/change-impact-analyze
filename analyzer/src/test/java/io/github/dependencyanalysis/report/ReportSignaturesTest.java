package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.bytecode.ServiceProviderRegistration;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.StructuralReference;
import io.github.dependencyanalysis.impact.StructuralReferenceKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests canonical JVM-exact report identities. */
class ReportSignaturesTest {

    /** Fixture artifact. */
    private static final ArtifactCoord ARTIFACT = new ArtifactCoord(
            "example", "library", "jar", "2");

    @Test
    void rendersMethodsFieldsClassesAndDescriptorChanges() {
        final MethodId method = new MethodId("example/app/Controller",
                "handle", "(Ljava/lang/String;)V", "app", "classes");
        final ChangePoint field = new ChangePoint(ARTIFACT,
                ChangePointKind.FIELD_REMOVED, "example/library/Api",
                "value", "Ljava/lang/String;", null, null);
        final ChangePoint changed = ChangePoint.withDescriptors(ARTIFACT,
                ChangePointKind.METHOD_DESCRIPTOR_CHANGED,
                "example/library/Api", "call",
                new MemberDescriptors("(I)V", "(J)V"), null, null);
        final ChangePoint type = new ChangePoint(ARTIFACT,
                ChangePointKind.CLASS_REMOVED, "example/library/Api",
                null, null, null, null);

        assertThat(ReportSignatures.method(method)).isEqualTo(
                "example.app.Controller#handle(Ljava/lang/String;)V");
        assertThat(ReportSignatures.changedMember(field)).isEqualTo(
                "example.library.Api#value:Ljava/lang/String;");
        assertThat(ReportSignatures.changedMember(changed)).isEqualTo(
                "example.library.Api#call(I)V → "
                        + "example.library.Api#call(J)V");
        assertThat(ReportSignatures.changedMember(type)).isEqualTo(
                "example.library.Api");
    }

    @Test
    void rendersServiceRegistrationAndStructuralFieldIdentity() {
        final ServiceProviderRegistration registration =
                new ServiceProviderRegistration(
                        "META-INF/services/example.library.Service",
                        "example/library/Service",
                        "example/library/Provider", ARTIFACT);
        final ChangePoint service =
                ChangePoint.serviceProviderRegistrationRemoved(registration);
        final StructuralReference field = new StructuralReference(
                "example/app/Config", CodeOrigin.PROJECT,
                StructuralReferenceKind.FIELD_TYPE,
                "service:Lexample/library/Service;",
                "example/library/Service", "fixture");

        assertThat(ReportSignatures.changedMember(service)).isEqualTo(
                "service example.library.Service → provider "
                        + "example.library.Provider "
                        + "[META-INF/services/example.library.Service]");
        assertThat(ReportSignatures.structuralOwner(field)).isEqualTo(
                "example.app.Config#service:Lexample/library/Service;");
    }
}
