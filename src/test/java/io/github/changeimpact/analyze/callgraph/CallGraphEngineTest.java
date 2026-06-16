package io.github.changeimpact.analyze.callgraph;

import io.github.changeimpact.analyze.build.BuildResult;
import io.github.changeimpact.analyze.build.ModuleBuildOutput;
import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for
 * {@link CallGraphEngine}.
 */
class CallGraphEngineTest {

    /** Proxy method max stack size. */
    private static final int PROXY_MAX_STACK = 3;

    /** Temp directory for classes. */
    @TempDir
    private Path tempDir;

    /** Diagnostic collector. */
    private final DiagnosticCollector
            diag = new DiagnosticCollector();

    /** Engine under test. */
    private final CallGraphEngine engine =
            new CallGraphEngine(diag);

    @Test
    void directCallCreatesEdge()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        writeClass(classesDir,
                "com/caller/Caller",
                "com/callee/Callee",
                "doWork", "()V",
                false, false);
        writeClass(classesDir,
                "com/callee/Callee",
                null,
                "doWork", "()V",
                false, true);
        final CallGraph cg =
                buildForDir(classesDir);
        assertThat(cg.getEdges())
                .isNotEmpty();
        assertThat(cg.getEdges())
                .extracting(
                        e -> e.getCallee()
                                .name())
                .contains("doWork");
        assertThat(cg.getStats()
                .methodCount())
                .isGreaterThan(0);
        assertThat(cg.getStats()
                .edgeCount())
                .isGreaterThan(0);
    }

    @Test
    void multiModuleCallCreatesEdge()
            throws Exception {
        final Path modA =
                tempDir.resolve("modA");
        final Path modB =
                tempDir.resolve("modB");
        Files.createDirectories(modA);
        Files.createDirectories(modB);
        writeClass(modA,
                "com/a/ServiceA",
                "com/b/ServiceB",
                "process", "()V",
                false, false);
        writeClass(modB,
                "com/b/ServiceB",
                null,
                "process", "()V",
                false, true);
        final ModuleBuildOutput outA =
                new ModuleBuildOutput(
                        modA, modA);
        final ModuleBuildOutput outB =
                new ModuleBuildOutput(
                        modB, modB);
        final BuildResult result =
                BuildResult.of(
                        List.of(outA, outB));
        final CallGraph cg =
                engine.build(result);
        assertThat(cg.getEdges())
                .isNotEmpty();
        assertThat(cg.getEdges())
                .extracting(
                        e -> e.getCallee()
                                .owner())
                .contains("com/b/ServiceB");
    }

    @Test
    void interfaceDispatchResolved()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("ifaces");
        Files.createDirectories(classesDir);
        writeInterface(classesDir,
                "com/api/Greeter",
                "greet", "()V");
        writeClassWithInterface(
                classesDir,
                "com/impl/HelloGreeter",
                "com/api/Greeter",
                "greet", "()V");
        writeCallerForInterface(
                classesDir,
                "com/app/App",
                "com/api/Greeter",
                "com/impl/HelloGreeter");
        final CallGraph cg =
                buildForDir(classesDir);
        assertThat(cg.getEdges())
                .extracting(
                        e -> e.getCallee()
                                .owner())
                .contains(
                        "com/impl/HelloGreeter");
    }

    @Test
    void overrideTrackingWorks()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("overrides");
        Files.createDirectories(classesDir);
        writeParentClass(classesDir,
                "com/base/Base",
                "action", "()V");
        writeChildClass(classesDir,
                "com/child/Child",
                "com/base/Base",
                "action", "()V");
        final CallGraph cg =
                buildForDir(classesDir);
        final Set<MethodId> overrides =
                cg.getOverrides(
                        new MethodId(
                                "com/base/Base",
                                "action",
                                "()V",
                                "",
                                "com/base/Base.class"));
        assertThat(overrides)
                .isNotEmpty();
        assertThat(overrides)
                .extracting(MethodId::owner)
                .contains("com/child/Child");
    }

    @Test
    void missingClassesDirThrows() {
        final Path missing =
                tempDir.resolve("nope");
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        tempDir, missing);
        final BuildResult result =
                BuildResult.of(
                        List.of(out));
        assertThatThrownBy(
                () -> engine.build(result))
                .isInstanceOf(
                        CallGraphException
                                .class);
    }

    @Test
    void statsArePopulated()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("stats");
        Files.createDirectories(classesDir);
        writeClass(classesDir,
                "com/x/X",
                "com/y/Y",
                "run", "()V",
                false, false);
        writeClass(classesDir,
                "com/y/Y",
                null,
                "run", "()V",
                false, true);
        final CallGraph cg =
                buildForDir(classesDir);
        assertThat(cg.getStats()
                .elapsedMillis())
                .isGreaterThanOrEqualTo(0);
        assertThat(cg.getStats()
                .memoryHighWatermarkBytes())
                .isGreaterThan(0);
    }

    @Test
    void testClassesAreExcluded()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("classes");
        final Path testClassesDir =
                tempDir.resolve(
                        "test-classes");
        Files.createDirectories(
                classesDir);
        Files.createDirectories(
                testClassesDir);
        writeClass(classesDir,
                "com/app/App",
                null,
                "run", "()V",
                false, true);
        writeClass(testClassesDir,
                "com/FooTest",
                null,
                "testSomething", "()V",
                false, true);
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        tempDir,
                        classesDir);
        final BuildResult result =
                BuildResult.of(
                        List.of(out));
        final CallGraph cg =
                engine.build(result);
        assertThat(cg.getMethods())
                .extracting(
                        MethodId::owner)
                .noneMatch(
                        o -> o.startsWith(
                                "com/FooTest"));
    }

    @Test
    void serviceLoaderCreatesServiceEdge()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("svc");
        Files.createDirectories(classesDir);
        writeInterface(classesDir,
                "com/api/Plugin",
                "execute", "()V");
        writeClassWithInterface(
                classesDir,
                "com/impl/MyPlugin",
                "com/api/Plugin",
                "execute", "()V");
        final Path svcDir =
                classesDir.resolve(
                        "META-INF/services");
        Files.createDirectories(svcDir);
        Files.writeString(
                svcDir.resolve(
                        "com.api.Plugin"),
                "com.impl.MyPlugin\n");
        final CallGraph cg =
                buildForDir(classesDir);
        assertThat(cg.getEdges())
                .extracting(CallEdge::getEdgeKind)
                .contains(EdgeKind.SERVICE);
        final CallEdge svcEdge =
                cg.getEdges().stream()
                        .filter(e -> e.getEdgeKind()
                                == EdgeKind.SERVICE)
                        .findFirst()
                        .orElseThrow();
        assertThat(svcEdge.getCaller().name())
                .isEqualTo("<service-loader>");
        assertThat(svcEdge.getCallee().name())
                .isEqualTo("<init>");
        assertThat(svcEdge.getCallee().owner())
                .isEqualTo("com/impl/MyPlugin");
        assertThat(svcEdge.getEvidence())
                .contains("META-INF/services");
    }

    @Test
    void serviceLoaderOverrideEnriched()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("svcovr");
        Files.createDirectories(classesDir);
        writeInterface(classesDir,
                "com/api/Plugin",
                "execute", "()V");
        writeClassWithInterface(
                classesDir,
                "com/impl/MyPlugin",
                "com/api/Plugin",
                "execute", "()V");
        final Path svcDir =
                classesDir.resolve(
                        "META-INF/services");
        Files.createDirectories(svcDir);
        Files.writeString(
                svcDir.resolve(
                        "com.api.Plugin"),
                "com.impl.MyPlugin\n");
        final CallGraph cg =
                buildForDir(classesDir);
        final MethodId ifaceMethod =
                new MethodId(
                        "com/api/Plugin",
                        "execute", "()V",
                        "",
                        "com/api/Plugin.class");
        assertThat(cg.getOverrides(ifaceMethod))
                .extracting(MethodId::owner)
                .contains("com/impl/MyPlugin");
    }

    @Test
    void providerNotInScopeEmitsInfo()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("noscope");
        Files.createDirectories(classesDir);
        writeInterface(classesDir,
                "com/api/Plugin",
                "execute", "()V");
        writeClass(classesDir,
                "com/app/Dummy",
                null,
                "run", "()V",
                false, true);
        final Path svcDir =
                classesDir.resolve(
                        "META-INF/services");
        Files.createDirectories(svcDir);
        Files.writeString(
                svcDir.resolve(
                        "com.api.Plugin"),
                "com.external.Missing\n");
        buildForDir(classesDir);
        assertThat(diag.getEvents())
                .extracting(
                        e -> e.getLevel().name()
                                + ":" + e.getMessage())
                .anyMatch(s -> s.contains(
                        "not in scope"));
    }

    @Test
    void literalClassForNameCreatesEdge()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("reflect");
        Files.createDirectories(classesDir);
        writeClass(classesDir,
                "com/target/Target",
                null,
                "run", "()V",
                false, true);
        writeClassWithForName(
                classesDir,
                "com/loader/Loader",
                "com.target.Target");
        final CallGraph cg =
                buildForDir(classesDir);
        assertThat(cg.getEdges())
                .extracting(CallEdge::getEdgeKind)
                .contains(
                        EdgeKind.REFLECTION_LITERAL);
        final CallEdge refEdge =
                cg.getEdges().stream()
                        .filter(e -> e.getEdgeKind()
                                == EdgeKind
                                .REFLECTION_LITERAL)
                        .findFirst()
                        .orElseThrow();
        assertThat(refEdge.getCallee().owner())
                .isEqualTo("com/target/Target");
        assertThat(refEdge.getCallee().name())
                .isEqualTo("<clinit>");
        assertThat(refEdge.getEvidence())
                .contains("Class.forName");
    }

    @Test
    void nonLiteralClassForNameWarns()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("nonlit");
        Files.createDirectories(classesDir);
        writeClassWithDynamicForName(
                classesDir,
                "com/loader/DynLoader");
        buildForDir(classesDir);
        assertThat(diag.getEvents())
                .extracting(
                        e -> e.getLevel().name()
                                + ":" + e.getMessage())
                .anyMatch(s -> s.contains(
                        "Non-literal")
                        && s.contains(
                                "Class.forName"));
    }

    @Test
    void dynamicProxyWarns()
            throws Exception {
        final Path classesDir =
                tempDir.resolve("proxy");
        Files.createDirectories(classesDir);
        writeClassWithProxy(
                classesDir,
                "com/proxy/ProxyCreator");
        buildForDir(classesDir);
        assertThat(diag.getEvents())
                .extracting(
                        e -> e.getLevel().name()
                                + ":" + e.getMessage())
                .anyMatch(s -> s.contains(
                        "Dynamic proxy"));
    }

    /**
     * Builds a call graph for a
     * single classes directory.
     *
     * @param dir classes directory
     * @return call graph
     */
    private CallGraph buildForDir(
            final Path dir) {
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        dir, dir);
        final BuildResult result =
                BuildResult.of(
                        List.of(out));
        return engine.build(result);
    }

    /**
     * Writes a simple class with a
     * method that optionally calls
     * another class.
     *
     * @param dir       output directory
     * @param className internal name
     * @param callOwner callee owner
     * @param callName  callee name
     * @param callDesc  callee descriptor
     * @param isIface   is interface
     * @param isCallee  is the callee
     */
    private void writeClass(
            final Path dir,
            final String className,
            final String callOwner,
            final String callName,
            final String callDesc,
            final boolean isIface,
            final boolean isCallee)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        final int acc = isIface
                ? Opcodes.ACC_PUBLIC
                | Opcodes.ACC_INTERFACE
                | Opcodes.ACC_ABSTRACT
                : Opcodes.ACC_PUBLIC;
        final String superName = isIface
                ? null
                : "java/lang/Object";
        cw.visit(Opcodes.V17, acc,
                className, null,
                superName, null);
        if (!isIface) {
            final MethodVisitor init =
                    cw.visitMethod(
                            Opcodes.ACC_PUBLIC,
                            "<init>", "()V",
                            null, null);
            init.visitCode();
            init.visitVarInsn(
                    Opcodes.ALOAD, 0);
            init.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/Object",
                    "<init>", "()V",
                    false);
            init.visitInsn(
                    Opcodes.RETURN);
            init.visitMaxs(1, 1);
            init.visitEnd();
        }
        final MethodVisitor mv =
                cw.visitMethod(
                        isIface
                                ? Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_ABSTRACT
                                : Opcodes.ACC_PUBLIC,
                        (isCallee || isIface)
                                ? callName
                                : "invoke",
                        (isCallee || isIface)
                                ? callDesc
                                : "()V",
                        null, null);
        if (!isIface && !isCallee
                && callOwner != null) {
            mv.visitCode();
            mv.visitTypeInsn(
                    Opcodes.NEW,
                    callOwner);
            mv.visitInsn(
                    Opcodes.DUP);
            mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    callOwner,
                    "<init>", "()V",
                    false);
            mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    callOwner,
                    callName, callDesc,
                    false);
            mv.visitInsn(
                    Opcodes.RETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        } else if (!isIface && isCallee) {
            mv.visitCode();
            mv.visitInsn(
                    Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        } else {
            mv.visitEnd();
        }
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(
                        out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes an interface class.
     *
     * @param dir       output directory
     * @param className internal name
     * @param mName     method name
     * @param mDesc     method descriptor
     */
    private void writeInterface(
            final Path dir,
            final String className,
            final String mName,
            final String mDesc)
            throws IOException {
        writeClass(dir, className,
                null, mName, mDesc,
                true, false);
    }

    /**
     * Writes a class that implements
     * an interface.
     *
     * @param dir       output directory
     * @param className internal name
     * @param iface     interface name
     * @param mName     method name
     * @param mDesc     method descriptor
     */
    private void writeClassWithInterface(
            final Path dir,
            final String className,
            final String iface,
            final String mName,
            final String mDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                new String[]{iface});
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V",
                false);
        init.visitInsn(
                Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        mName, mDesc,
                        null, null);
        mv.visitCode();
        mv.visitInsn(
                Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(
                        out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a caller that invokes
     * an interface method.
     *
     * @param dir       output directory
     * @param className caller name
     * @param iface     interface name
     * @param impl      impl class name
     */
    private void writeCallerForInterface(
            final Path dir,
            final String className,
            final String iface,
            final String impl)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V",
                false);
        init.visitInsn(
                Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "run", "()V",
                        null, null);
        mv.visitCode();
        mv.visitTypeInsn(
                Opcodes.NEW, impl);
        mv.visitInsn(
                Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                impl, "<init>",
                "()V", false);
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                iface, "greet",
                "()V", true);
        mv.visitInsn(
                Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(
                        out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a parent class with a
     * virtual method.
     *
     * @param dir       output directory
     * @param className internal name
     * @param mName     method name
     * @param mDesc     method descriptor
     */
    private void writeParentClass(
            final Path dir,
            final String className,
            final String mName,
            final String mDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V",
                false);
        init.visitInsn(
                Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        mName, mDesc,
                        null, null);
        mv.visitCode();
        mv.visitInsn(
                Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(
                        out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a child class that
     * overrides a parent method.
     *
     * @param dir       output directory
     * @param className internal name
     * @param parent    parent name
     * @param mName     method name
     * @param mDesc     method descriptor
     */
    private void writeChildClass(
            final Path dir,
            final String className,
            final String parent,
            final String mName,
            final String mDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                parent, null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                parent, "<init>",
                "()V", false);
        init.visitInsn(
                Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        mName, mDesc,
                        null, null);
        mv.visitCode();
        mv.visitInsn(
                Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(
                        out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a class that calls
     * Class.forName with a literal.
     *
     * @param dir       output directory
     * @param className caller internal name
     * @param target    target binary name
     */
    private void writeClassWithForName(
            final Path dir,
            final String className,
            final String target)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object", null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "load", "()V",
                        null, null);
        mv.visitCode();
        mv.visitLdcInsn(target);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)"
                        + "Ljava/lang/Class;",
                false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a class that calls
     * Class.forName with a variable.
     *
     * @param dir       output directory
     * @param className caller internal name
     */
    private void writeClassWithDynamicForName(
            final Path dir,
            final String className)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object", null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "load",
                        "(Ljava/lang/String;)V",
                        null, null);
        mv.visitCode();
        mv.visitVarInsn(
                Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)"
                        + "Ljava/lang/Class;",
                false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 2);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(out)) {
            os.write(cw.toByteArray());
        }
    }

    /**
     * Writes a class that calls
     * Proxy.newProxyInstance.
     *
     * @param dir       output directory
     * @param className caller internal name
     */
    private void writeClassWithProxy(
            final Path dir,
            final String className)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object", null);
        final MethodVisitor init =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "<init>", "()V",
                        null, null);
        init.visitCode();
        init.visitVarInsn(
                Opcodes.ALOAD, 0);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "create", "()V",
                        null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitTypeInsn(
                Opcodes.ANEWARRAY,
                "java/lang/Class");
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/lang/reflect/Proxy",
                "newProxyInstance",
                "(Ljava/lang/ClassLoader;"
                        + "[Ljava/lang/Class;"
                        + "Ljava/lang/"
                        + "reflect/"
                        + "InvocationHandler;)"
                        + "Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(PROXY_MAX_STACK, 1);
        mv.visitEnd();
        cw.visitEnd();
        final Path out = dir.resolve(
                className + ".class");
        Files.createDirectories(
                out.getParent());
        try (OutputStream os =
                Files.newOutputStream(out)) {
            os.write(cw.toByteArray());
        }
    }
}
