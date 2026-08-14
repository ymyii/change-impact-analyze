package io.github.dependencyanalysis.callgraph.entrypoint;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
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

// Wiki: wiki/features/call-graph-engine.md - Actors / Entrypoints.
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
        int methodCount = 0;
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
                final int[] selfInnerAccess = new int[1];
                final int[] concreteNonPrivateMethods = new int[1];
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInnerClass(
                            final String innerName,
                            final String outerName,
                            final String simpleName,
                            final int access) {
                        if (name.equals(innerName)) {
                            selfInnerAccess[0] = access;
                        }
                    }

                    @Override
                    public MethodVisitor visitMethod(
                            final int access, final String methodName,
                            final String descriptor, final String signature,
                            final String[] exceptions) {
                        if ((access & (Opcodes.ACC_PRIVATE
                                | Opcodes.ACC_ABSTRACT)) == 0) {
                            concreteNonPrivateMethods[0]++;
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
                if (((reader.getAccess() | selfInnerAccess[0])
                        & Opcodes.ACC_PRIVATE) != 0) {
                    continue;
                }
                final Path previous = selectedClasses.putIfAbsent(name, file);
                if (previous != null) {
                    throw new CallGraphException(
                            "Duplicate PROJECT entrypoint class " + name
                                    + ": " + previous + " and " + file);
                }
                methodCount += concreteNonPrivateMethods[0];
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
                methodCount);
    }
}
