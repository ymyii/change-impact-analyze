package io.github.dependencyanalysis.callgraph;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Lightweight target/classes scan used before expensive module analysis. */
public final class EntrypointClassScanner {

    /**
     * Counts selected executable PROJECT methods without reading method code.
     *
     * @param classes target/classes directory
     * @param selection user boundary
     * @return selected class/method counts
     */
    public EntrypointSelectionMetrics scan(
            final Path classes,
            final EntrypointSelection selection) {
        if (!Files.isDirectory(classes)) {
            return new EntrypointSelectionMetrics(0, 0, 0);
        }
        final int[] classCount = new int[1];
        final int[] methodCount = new int[1];
        try (var stream = Files.walk(classes)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(value -> value.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                final ClassReader reader = new ClassReader(
                        Files.readAllBytes(file));
                if (!selection.matchesInternalName(reader.getClassName())) {
                    continue;
                }
                classCount[0]++;
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            final int access, final String name,
                            final String descriptor, final String signature,
                            final String[] exceptions) {
                        if ((access & Opcodes.ACC_ABSTRACT) == 0) {
                            methodCount[0]++;
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
            }
        } catch (IOException | RuntimeException exception) {
            throw new CallGraphException(
                    "Unable to scan PROJECT entrypoint classes", exception);
        }
        return new EntrypointSelectionMetrics(
                classCount[0], methodCount[0], 0);
    }
}
