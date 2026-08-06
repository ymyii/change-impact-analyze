package io.github.dependencyanalysis.callgraph;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight target/classes index used before expensive module analysis. */
public final class EntrypointClassScanner {

    /**
     * Indexes selected executable PROJECT classes without reading method code.
     *
     * @param classes target/classes directory
     * @param selection user boundary
     * @return stable selected class index
     */
    public EntrypointClassIndex scan(
            final Path classes,
            final EntrypointSelection selection) {
        if (!Files.isDirectory(classes)) {
            return new EntrypointClassIndex(List.of(), 0);
        }
        final Map<String, Path> selectedClasses = new LinkedHashMap<>();
        final int[] methodCount = new int[1];
        try (var stream = Files.walk(classes)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(value -> value.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                final ClassReader reader = new ClassReader(
                        Files.readAllBytes(file));
                final String name = reader.getClassName();
                if ("module-info".equals(name)
                        || (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0
                        || !selection.matchesInternalName(name)) {
                    continue;
                }
                final Path previous = selectedClasses.putIfAbsent(name, file);
                if (previous != null) {
                    throw new CallGraphException(
                            "Duplicate PROJECT entrypoint class " + name
                                    + ": " + previous + " and " + file);
                }
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            final int access, final String methodName,
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
            if (exception instanceof CallGraphException) {
                throw (CallGraphException) exception;
            }
            throw new CallGraphException(
                    "Unable to index PROJECT entrypoint classes", exception);
        }
        return new EntrypointClassIndex(
                selectedClasses.keySet().stream().sorted().toList(),
                methodCount[0]);
    }
}
