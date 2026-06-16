package io.github.changeimpact.analyze.impact;

import io.github.changeimpact.analyze.build.BuildResult;
import io.github.changeimpact.analyze.build.ModuleBuildOutput;
import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.bytecode.ChangePointKind;
import io.github.changeimpact.analyze.callgraph.CallGraph;
import io.github.changeimpact.analyze.callgraph.CallGraphEngine;
import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link ImpactTracer}.
 */
class ImpactTracerTest {

    /** Dependency owner. */
    private static final String DEP =
            "com/dep/DepLib";

    /** Temp directory. */
    @TempDir
    private Path tempDir;

    /** Diagnostic collector. */
    private DiagnosticCollector diag;

    /** Call graph engine. */
    private CallGraphEngine cgEngine;

    /** Tracer under test. */
    private ImpactTracer tracer;

    /** Artifact for change pts. */
    private ArtifactCoord artifact;

    @BeforeEach
    void setUp() {
        diag = new DiagnosticCollector();
        cgEngine = new CallGraphEngine(
                diag);
        tracer = new ImpactTracer(diag);
        artifact = new ArtifactCoord(
                "com.dep", "deplib",
                "jar", "2.0");
    }

    @Test
    void methodBodyChangedAndReached()
            throws Exception {
        final Path dir =
                tempDir.resolve("reach");
        Files.createDirectories(dir);
        writeHelperClass(dir,
                "com/app/Helper",
                DEP, "doWork", "()V");
        writeCallerClass(dir,
                "com/app/App",
                "com/app/Helper",
                "invoke", "()V");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        DEP, "doWork",
                        "()V",
                        "old", "new");
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .hasSize(1);
        final ImpactPath path =
                res.getPaths().get(0);
        assertThat(path.getChangePoint())
                .isEqualTo(cp);
        assertThat(path.getEdges())
                .isNotEmpty();
    }

    @Test
    void methodBodyChangedNotReached()
            throws Exception {
        final Path dir =
                tempDir.resolve("notreach");
        Files.createDirectories(dir);
        writeSimpleClass(dir,
                "com/app/App",
                "run");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        DEP, "doWork",
                        "()V",
                        "old", "new");
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .isEmpty();
        assertThat(res.getNotReportedCount(
                NotReportedReason
                        .NO_SEED_FOUND))
                .isEqualTo(1);
    }

    @Test
    void methodRemovedAndCalled()
            throws Exception {
        final Path dir =
                tempDir.resolve("removed");
        Files.createDirectories(dir);
        writeCallerClass(dir,
                "com/app/App",
                DEP, "doWork", "()V");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_REMOVED,
                        DEP, "doWork",
                        "()V",
                        null, null);
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .hasSize(1);
        assertThat(res.getPaths().get(0)
                .getChangePoint())
                .isEqualTo(cp);
    }

    @Test
    void fieldRemovedAndAccessed()
            throws Exception {
        final Path dir =
                tempDir.resolve("field");
        Files.createDirectories(dir);
        writeFieldAccessClass(dir,
                "com/app/App",
                DEP, "VALUE", "I");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .FIELD_REMOVED,
                        DEP, "VALUE",
                        "I",
                        null, null);
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .hasSize(1);
        assertThat(res.getPaths().get(0)
                .getChangePoint())
                .isEqualTo(cp);
    }

    @Test
    void circularCallNoDeadlock()
            throws Exception {
        final Path dir =
                tempDir.resolve("cycle");
        Files.createDirectories(dir);
        writeCircularA(dir,
                "com/app/A",
                "com/app/B");
        writeCircularB(dir,
                "com/app/B",
                "com/app/A",
                DEP, "doWork", "()V");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        DEP, "doWork",
                        "()V",
                        "old", "new");
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .isNotEmpty();
    }

    @Test
    void crossModuleBoundary()
            throws Exception {
        final Path modA =
                tempDir.resolve("modA");
        final Path modB =
                tempDir.resolve("modB");
        Files.createDirectories(modA);
        Files.createDirectories(modB);
        writeCallerClass(modA,
                "com/a/App",
                "com/b/Helper",
                "invoke", "()V");
        writeHelperClass(modB,
                "com/b/Helper",
                DEP, "doWork", "()V");
        final ModuleBuildOutput outA =
                new ModuleBuildOutput(
                        modA, modA);
        final ModuleBuildOutput outB =
                new ModuleBuildOutput(
                        modB, modB);
        final BuildResult br =
                BuildResult.of(
                        List.of(outA,
                                outB));
        final CallGraph cg =
                cgEngine.build(br);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        DEP, "doWork",
                        "()V",
                        "old", "new");
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .isNotEmpty();
        final ImpactPath path =
                res.getPaths().get(0);
        assertThat(path.getCrossModuleBoundaries())
                .isNotEmpty();
    }

    @Test
    void addedKindNotReported()
            throws Exception {
        final Path dir =
                tempDir.resolve("added");
        Files.createDirectories(dir);
        writeSimpleClass(dir,
                "com/app/App",
                "run");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_ADDED,
                        DEP, "newMethod",
                        "()V",
                        null, null);
        final BuildResult br =
                buildResult(dir);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .isEmpty();
        assertThat(res.getNotReportedCount(
                NotReportedReason
                        .CHANGE_KIND_NOT_APPLICABLE))
                .isEqualTo(1);
    }

    @Test
    void stableSortOrder()
            throws Exception {
        final Path dir =
                tempDir.resolve("sort");
        Files.createDirectories(dir);
        writeCallerClass(dir,
                "com/b/Beta",
                DEP, "alpha", "()V");
        writeCallerClass(dir,
                "com/a/Alpha",
                DEP, "beta", "()V");
        final CallGraph cg =
                buildGraph(dir);
        final ChangePoint cpBeta =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_REMOVED,
                        DEP, "alpha",
                        "()V",
                        null, null);
        final ChangePoint cpAlpha =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_REMOVED,
                        DEP, "beta",
                        "()V",
                        null, null);
        final BuildResult br =
                buildResult(dir);
        final List<ChangePoint> cps =
                new ArrayList<>();
        cps.add(cpBeta);
        cps.add(cpAlpha);
        final ImpactResult res =
                tracer.trace(
                        cps, cg, br);
        assertThat(res.getPaths())
                .hasSize(2);
        final Comparator<ImpactPath> cmp =
                ImpactTracer
                        .pathComparator();
        final List<ImpactPath> sorted =
                new ArrayList<>(
                        res.getPaths());
        sorted.sort(cmp);
        assertThat(res.getPaths())
                .containsExactlyElementsOf(
                        sorted);
    }

    @Test
    void incompleteChainNotReported()
            throws Exception {
        final Path scanDir =
                tempDir.resolve(
                        "incompleteScan");
        final Path cgDir =
                tempDir.resolve(
                        "incompleteCg");
        Files.createDirectories(
                scanDir);
        Files.createDirectories(
                cgDir);
        writeCallerClass(scanDir,
                "com/app/App",
                DEP, "doWork",
                "()V");
        writeSimpleClass(cgDir,
                "com/other/Other",
                "unrelated");
        final BuildResult br =
                buildResult(scanDir);
        final CallGraph cg =
                buildGraph(cgDir);
        final ChangePoint cp =
                new ChangePoint(
                        artifact,
                        ChangePointKind
                                .METHOD_REMOVED,
                        DEP, "doWork",
                        "()V",
                        null, null);
        final ImpactResult res =
                tracer.trace(
                        List.of(cp), cg,
                        br);
        assertThat(res.getPaths())
                .isEmpty();
        assertThat(res.getNotReportedCount(
                NotReportedReason
                        .INCOMPLETE_CHAIN))
                .isGreaterThan(0);
    }

    /**
     * Builds a call graph for a
     * single directory.
     *
     * @param dir classes dir
     * @return call graph
     */
    private CallGraph buildGraph(
            final Path dir) {
        final BuildResult br =
                buildResult(dir);
        return cgEngine.build(br);
    }

    /**
     * Creates a build result
     * for a single directory.
     *
     * @param dir classes dir
     * @return build result
     */
    private BuildResult buildResult(
            final Path dir) {
        final ModuleBuildOutput out =
                new ModuleBuildOutput(
                        dir, dir);
        return BuildResult.of(
                List.of(out));
    }

    /**
     * Writes a class whose method
     * calls another class method.
     *
     * @param dir       output dir
     * @param className internal name
     * @param callOwner callee owner
     * @param callName  callee name
     * @param callDesc  callee desc
     */
    private void writeCallerClass(
            final Path dir,
            final String className,
            final String callOwner,
            final String callName,
            final String callDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "invoke", "()V",
                        null, null);
        mv.visitCode();
        mv.visitTypeInsn(
                Opcodes.NEW,
                callOwner);
        mv.visitInsn(Opcodes.DUP);
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
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes a helper class with
     * a method that calls a
     * dependency method.
     *
     * @param dir       output dir
     * @param className internal name
     * @param depOwner  dep owner
     * @param depName   dep method
     * @param depDesc   dep desc
     */
    private void writeHelperClass(
            final Path dir,
            final String className,
            final String depOwner,
            final String depName,
            final String depDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "invoke", "()V",
                        null, null);
        mv.visitCode();
        mv.visitTypeInsn(
                Opcodes.NEW,
                depOwner);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                depOwner,
                "<init>", "()V",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                depOwner,
                depName, depDesc,
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes a class that accesses
     * a static field.
     *
     * @param dir       output dir
     * @param className internal name
     * @param fieldOwner field owner
     * @param fieldName field name
     * @param fieldDesc field desc
     */
    private void writeFieldAccessClass(
            final Path dir,
            final String className,
            final String fieldOwner,
            final String fieldName,
            final String fieldDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "invoke", "()V",
                        null, null);
        mv.visitCode();
        mv.visitFieldInsn(
                Opcodes.GETSTATIC,
                fieldOwner,
                fieldName, fieldDesc);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes a simple class with
     * a named method.
     *
     * @param dir       output dir
     * @param className internal name
     * @param mName     method name
     */
    private void writeSimpleClass(
            final Path dir,
            final String className,
            final String mName)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        mName, "()V",
                        null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes class A of a
     * circular call pair.
     *
     * @param dir       output dir
     * @param className A name
     * @param otherName B name
     */
    private void writeCircularA(
            final Path dir,
            final String className,
            final String otherName)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "run", "()V",
                        null, null);
        mv.visitCode();
        mv.visitTypeInsn(
                Opcodes.NEW,
                otherName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                otherName,
                "<init>", "()V",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                otherName,
                "run", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes class B of a
     * circular call pair that
     * also calls a dependency.
     *
     * @param dir       output dir
     * @param className B name
     * @param otherName A name
     * @param depOwner  dep owner
     * @param depName   dep method
     * @param depDesc   dep desc
     */
    private void writeCircularB(
            final Path dir,
            final String className,
            final String otherName,
            final String depOwner,
            final String depName,
            final String depDesc)
            throws IOException {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                className, null,
                "java/lang/Object",
                null);
        writeInit(cw);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "run", "()V",
                        null, null);
        mv.visitCode();
        mv.visitTypeInsn(
                Opcodes.NEW,
                depOwner);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                depOwner,
                "<init>", "()V",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                depOwner,
                depName, depDesc,
                false);
        mv.visitTypeInsn(
                Opcodes.NEW,
                otherName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                otherName,
                "<init>", "()V",
                false);
        mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                otherName,
                "run", "()V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        writeToFile(dir, className, cw);
    }

    /**
     * Writes a default init
     * method calling super.
     *
     * @param cw class writer
     */
    private void writeInit(
            final ClassWriter cw) {
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
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();
    }

    /**
     * Writes class bytes to file.
     *
     * @param dir       output dir
     * @param className internal name
     * @param cw        class writer
     */
    private void writeToFile(
            final Path dir,
            final String className,
            final ClassWriter cw)
            throws IOException {
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
}
