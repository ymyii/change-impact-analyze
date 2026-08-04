package io.github.dependencyanalysis.impact;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests class metadata evidence extraction. */
class StructuralImpactScannerTest {

    @Test
    void detectsStructuralMetadataKinds() {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "app/Consumer",
                "<T:Ldep/RemovedGeneric;>Ldep/RemovedSuper;",
                "dep/RemovedSuper",
                new String[]{"dep/RemovedInterface"});
        final org.objectweb.asm.AnnotationVisitor annotation =
                writer.visitAnnotation(
                        "Ldep/RemovedAnnotation;", true);
        annotation.visit("type",
                Type.getObjectType("dep/RemovedAnnotationValue"));
        annotation.visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "field",
                "Ldep/RemovedField;",
                "Ldep/RemovedField;", null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC, "method",
                "(Ldep/RemovedParameter;)V", null,
                new String[]{"dep/RemovedException"}).visitEnd();
        writer.visitEnd();
        final Set<String> targets = Set.of(
                "dep/RemovedGeneric", "dep/RemovedSuper",
                "dep/RemovedInterface", "dep/RemovedAnnotation",
                "dep/RemovedAnnotationValue",
                "dep/RemovedField", "dep/RemovedParameter",
                "dep/RemovedException");

        final Map<String, Set<String>> evidence =
                new StructuralImpactScanner().metadataEvidence(
                        writer.toByteArray(), targets);

        assertThat(evidence.get("dep/RemovedSuper"))
                .contains("SUPERCLASS", "CLASS_SIGNATURE");
        assertThat(evidence.get("dep/RemovedInterface"))
                .contains("INTERFACE");
        assertThat(evidence.get("dep/RemovedAnnotation"))
                .contains("CLASS_ANNOTATION");
        assertThat(evidence.get("dep/RemovedAnnotationValue"))
                .contains("CLASS_ANNOTATION_VALUE");
        assertThat(evidence.get("dep/RemovedGeneric"))
                .contains("CLASS_SIGNATURE");
        assertThat(evidence.get("dep/RemovedField"))
                .contains("FIELD_DESCRIPTOR:field",
                        "FIELD_SIGNATURE:field");
        assertThat(evidence.get("dep/RemovedParameter"))
                .contains("METHOD_DESCRIPTOR:method(Ldep/"
                        + "RemovedParameter;)V");
        assertThat(evidence.get("dep/RemovedException"))
                .contains("THROWS:method(Ldep/RemovedParameter;)V");
    }
}
