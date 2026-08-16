package io.github.dependencyanalysis.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Canonical ASM text renderer for one exact JVM method. */
public final class MethodBytecodeTextRenderer {

    /** Utility class. */
    private MethodBytecodeTextRenderer() {
    }

    /**
     * Renders one method from a class file.
     *
     * @param classBytes complete class file bytes
     * @param name JVM method name
     * @param descriptor JVM method descriptor
     * @return ASM Textifier output
     * @throws IOException when the method is absent
     */
    public static String render(
            final byte[] classBytes,
            final String name,
            final String descriptor) throws IOException {
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        final ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(classBytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                final Textifier printer = new Textifier();
                method.accept(new TraceMethodVisitor(printer));
                final StringWriter output = new StringWriter();
                printer.print(new PrintWriter(output));
                return output.toString();
            }
        }
        throw new IOException("Method not found: " + name + descriptor);
    }
}
