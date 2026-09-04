package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.impact.AnalysisConcurrency;
import io.github.dependencyanalysis.impact.AnalysisMode;
import io.github.dependencyanalysis.impact.AnalysisRunConfiguration;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.AnalysisStatus;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.ChangePointDisposition;
import io.github.dependencyanalysis.impact.ChangePointTerminal;
import io.github.dependencyanalysis.impact.CodeComparisonEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonStatus;
import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.EvidenceKind;
import io.github.dependencyanalysis.impact.EvidenceLocation;
import io.github.dependencyanalysis.impact.EvidenceMechanism;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactPathRootKind;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.ReferenceEvidence;
import io.github.dependencyanalysis.impact.ReferenceTarget;
import io.github.dependencyanalysis.impact.StructuralReference;
import io.github.dependencyanalysis.impact.StructuralReferenceKind;
import io.github.dependencyanalysis.impact.StructuralReferencePath;
import io.github.dependencyanalysis.impact.UnifiedDiffHunk;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Publishes the deterministic offline report consumed by Playwright. */
class ReportBrowserFixtureIT {

    /** Small enough to force multiple shards without a large fixture. */
    private static final int SHARD_BYTES = 700;

    /** Changed members required to exercise the default page boundary. */
    private static final int CHANGED_MEMBER_COUNT = 25;

    /** Impact rows required to exercise Affected Paths pagination. */
    private static final int IMPACT_PATH_COUNT = 41;

    /** JVM target rendered in the fixture report. */
    private static final int TARGET_JAVA_MAJOR = 8;

    @Test
    void publishesDeterministicOfflineBrowserFixture() throws Exception {
        final Path projectRoot = Path.of(System.getProperty(
                "cia.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        final Path fixtureRoot = projectRoot.resolve(
                "target/playwright-report-fixture");
        final Path output = fixtureRoot.resolve("impact.html");
        Files.createDirectories(fixtureRoot);

        final AnalysisRunResult run = browserRun(projectRoot);
        final Path runtimeRoot = projectRoot.resolve(
                "target/playwright-report-runtime");
        try (MavenDependencyPluginRuntime plugin =
                     new MavenDependencyPluginRuntimeManager().prepare(
                             runtimeRoot, List.of(), null)) {
            new PerModuleHtmlReportGenerator(
                    new DiagnosticLog(), SHARD_BYTES).generate(
                            run, new PreflightReport(List.of()),
                            mavenRuntime(projectRoot), plugin,
                            javaRuntime(projectRoot), output);
        }

        final Path moduleDirectory = fixtureRoot.resolve("impact-modules");
        assertThat(output).isRegularFile();
        assertThat(moduleDirectory).isDirectory();
        try (Stream<Path> pages = Files.list(moduleDirectory)) {
            assertThat(pages.filter(Files::isRegularFile).toList())
                    .hasSize(2);
        }
        try (Stream<Path> shards = Files.walk(moduleDirectory)) {
            assertThat(shards.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".js")).count()).isGreaterThan(8L);
        }
    }

    private AnalysisRunResult browserRun(final Path projectRoot) {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "io.browserfixture", "application", "jar", "1.0.0"),
                Path.of("application"));
        final ArtifactCoord ordersOld = new ArtifactCoord(
                "com.acme", "orders-api", "jar", "1.0.0");
        final ArtifactCoord ordersNew = new ArtifactCoord(
                "com.acme", "orders-api", "jar", "2.0.0");
        final ArtifactCoord billingOld = new ArtifactCoord(
                "org.browserfixture", "billing-spi", "jar", "3.1.0");
        final ArtifactCoord billingNew = new ArtifactCoord(
                "org.browserfixture", "billing-spi", "jar", "4.0.0");
        final DependencyUpgradeKey orders = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE, ordersOld, ordersNew);
        final DependencyUpgradeKey billing = new DependencyUpgradeKey(
                moduleId, DependencyScope.RUNTIME, billingOld, billingNew);

        final List<BoundChangePoint> members = changedMembers(
                orders, billing, ordersNew, billingNew);
        final BoundChangePoint available = members.get(0);
        final BoundChangePoint unavailable = members.get(1);
        final BoundChangePoint structural = members.get(2);
        final List<ImpactPath> impactPaths = impactPaths(
                available, unavailable);
        final StructuralReferencePath structuralPath =
                structuralPath(structural);

        final List<DependencyChange> dependencyChanges = List.of(
                new DependencyChange(ChangeType.VERSION_CHANGED,
                        ordersOld, ordersNew, DependencyScope.COMPILE,
                        moduleId.stableKey()),
                new DependencyChange(ChangeType.VERSION_CHANGED,
                        billingOld, billingNew, DependencyScope.RUNTIME,
                        moduleId.stableKey()));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH,
                projectRoot.resolve("target/playwright-project-classes"),
                List.of(), List.of(ordersNew, billingNew),
                List.of(ordersOld, billingOld),
                new ModuleChangeSet(dependencyChanges, members, List.of()));

        final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                new LinkedHashMap<>();
        members.forEach(member -> dispositions.put(
                member, ChangePointDisposition.NO_PROJECT_PATH));
        dispositions.put(available, ChangePointDisposition.IMPACT_REPORTED);
        dispositions.put(unavailable, ChangePointDisposition.IMPACT_REPORTED);
        dispositions.put(structural, ChangePointDisposition.IMPACT_REPORTED);

        final Map<BoundChangePoint, CodeComparisonEvidence> comparisons =
                new LinkedHashMap<>();
        comparisons.put(available, availableDiff("lookup"));
        comparisons.put(unavailable, new CodeComparisonEvidence(
                CodeComparisonStatus.UNAVAILABLE, List.of(),
                "fixture comparison unavailable"));
        comparisons.put(structural, availableDiff("status"));

        final ModuleAnalysisResult module = new ModuleAnalysisResult.Builder(
                unit).impactPaths(impactPaths)
                .structuralPaths(List.of(structuralPath))
                .dispositions(dispositions)
                .codeComparisons(comparisons)
                .elapsedMillis(42L)
                .build();
        return new AnalysisRunResult(
                AnalysisMode.REACTOR, AnalysisStatus.SUCCESS,
                dependencyChanges, List.of(module),
                new AnalysisConcurrency(2, 1, 1, 1), Map.of(),
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA,
                        CallGraphAlgorithm.defaultKObjDepth(),
                        WalaReflectionOptions.defaultOptions(),
                        DependencyAnalysisScopeMode.defaultMode(),
                        JdkModelSelection.NONE));
    }

    private List<BoundChangePoint> changedMembers(
            final DependencyUpgradeKey orders,
            final DependencyUpgradeKey billing,
            final ArtifactCoord ordersArtifact,
            final ArtifactCoord billingArtifact) {
        final List<BoundChangePoint> result = new ArrayList<>();
        result.add(bound(orders, new ChangePoint(ordersArtifact,
                ChangePointKind.METHOD_BODY_CHANGED,
                "com/acme/orders/OrderApi", "lookup",
                "(Ljava/lang/String;)Ljava/lang/String;", "old", "new")));
        result.add(bound(billing, new ChangePoint(billingArtifact,
                ChangePointKind.METHOD_REMOVED,
                "org/browserfixture/billing/BillingGateway",
                "changedMemberNeedle", "(JLjava/lang/String;)V",
                null, null)));
        result.add(bound(orders, ChangePoint.withDescriptors(
                ordersArtifact, ChangePointKind.FIELD_DESCRIPTOR_CHANGED,
                "com/acme/orders/OrderState", "status",
                new MemberDescriptors("I", "Ljava/lang/String;"),
                null, null)));
        result.add(bound(orders, new ChangePoint(ordersArtifact,
                ChangePointKind.METHOD_REMOVED,
                "com/acme/orders/OrderApi", "lookup",
                "(J)Ljava/lang/String;", null, null)));
        result.add(bound(orders, ChangePoint.withDescriptors(
                ordersArtifact, ChangePointKind.METHOD_DESCRIPTOR_CHANGED,
                "com/acme/orders/OrderApi", "convert",
                new MemberDescriptors("(I)Ljava/lang/String;",
                        "(J)Ljava/lang/String;"), null, null)));
        result.add(bound(orders, new ChangePoint(ordersArtifact,
                ChangePointKind.FIELD_REMOVED,
                "com/acme/orders/OrderApi", "DEFAULT_TIMEOUT", "J",
                null, null)));
        result.add(bound(orders, new ChangePoint(ordersArtifact,
                ChangePointKind.CLASS_REMOVED,
                "com/acme/orders/LegacyOrder", null, null,
                null, null)));
        result.add(bound(billing, new ChangePoint(billingArtifact,
                ChangePointKind.METHOD_REMOVED,
                "org/browserfixture/billing/BillingRequest", "<init>",
                "(Ljava/lang/String;Ljava/math/BigDecimal;)V",
                null, null)));
        result.add(bound(orders, new ChangePoint(ordersArtifact,
                ChangePointKind.METHOD_BODY_CHANGED,
                "com/acme/orders/ExtremelyLongCompatibilitySurfaceFor"
                        + "ResponsiveLayoutVerification",
                "calculateCustomerSpecificOrderCompatibilityResult",
                "(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)"
                        + "Ljava/util/concurrent/CompletionStage;",
                "old-long", "new-long")));
        for (int index = result.size();
             index < CHANGED_MEMBER_COUNT; index++) {
            final boolean useOrders = index % 2 == 0;
            result.add(bound(useOrders ? orders : billing,
                    new ChangePoint(
                            useOrders ? ordersArtifact : billingArtifact,
                            ChangePointKind.METHOD_REMOVED,
                            useOrders ? "com/acme/orders/UnusedApi"
                                    : "org/browserfixture/billing/UnusedApi",
                            String.format("unused%02d", index),
                            "(Ljava/lang/String;I)V", null, null)));
        }
        return List.copyOf(result);
    }

    private List<ImpactPath> impactPaths(
            final BoundChangePoint available,
            final BoundChangePoint unavailable) {
        final List<ImpactPath> result = new ArrayList<>();
        final QueryNode multiRoot = projectMethod(
                "io/browserfixture/application/MultiMethodOnlyController",
                "review", "(Ljava/lang/String;)V");
        final QueryNode multiService = projectMethod(
                "io/browserfixture/application/MultiMethodOnlyService",
                "load", "(Ljava/lang/String;I)Ljava/lang/String;");
        result.add(impactPath(List.of(multiRoot, multiService), available,
                ImpactClassification.TRANSITIVE));
        for (int index = 1; index < IMPACT_PATH_COUNT - 1; index++) {
            final String owner = index == 7
                    ? "io/browserfixture/application/PathOnlyNeedleController"
                    : String.format(
                            "io/browserfixture/application/Root%02d", index);
            result.add(impactPath(List.of(projectMethod(owner, "invoke",
                    "()V")), available, ImpactClassification.DIRECT));
        }
        result.add(impactPath(List.of(projectMethod(
                        "io/browserfixture/application/BillingController",
                        "charge", "(J)V")), unavailable,
                ImpactClassification.DIRECT));
        return List.copyOf(result);
    }

    private StructuralReferencePath structuralPath(
            final BoundChangePoint structural) {
        final QueryNode root = projectMethod(
                "io/browserfixture/application/OrderConfiguration",
                "configure", "(Ljava/lang/String;)V");
        return new StructuralReferencePath(structural,
                new StructuralReference(
                        "io/browserfixture/application/OrderConfiguration",
                        CodeOrigin.PROJECT,
                        StructuralReferenceKind.FIELD_TYPE,
                        "status:Ljava/lang/String;",
                        "com/acme/orders/OrderState",
                        "browser fixture field metadata"),
                List.of(root), ImpactClassification.DIRECT);
    }

    private ImpactPath impactPath(
            final List<QueryNode> nodes,
            final BoundChangePoint member,
            final ImpactClassification classification) {
        final ChangePoint point = member.getChangePoint();
        final ReferenceEvidence evidence = new ReferenceEvidence(
                Optional.empty(), new ReferenceTarget(point.getOwner(),
                point.getName() == null ? "" : point.getName(),
                point.getDescriptor() == null ? "" : point.getDescriptor()),
                EvidenceKind.METHOD_REFERENCE,
                EvidenceMechanism.METHOD_DECLARATION,
                new EvidenceLocation("playwright-report-fixture", 0),
                "deterministic browser fixture");
        return new ImpactPath(nodes,
                new ChangePointTerminal(member, evidence), classification,
                ImpactPathRootKind.METHOD);
    }

    private QueryNode projectMethod(
            final String owner,
            final String name,
            final String descriptor) {
        return new FixtureQueryNode(new MethodId(owner, name, descriptor,
                "application", "playwright-project"), CodeOrigin.PROJECT);
    }

    private BoundChangePoint bound(
            final DependencyUpgradeKey upgrade,
            final ChangePoint point) {
        return new BoundChangePoint(upgrade, point);
    }

    private CodeComparisonEvidence availableDiff(final String member) {
        return new CodeComparisonEvidence(CodeComparisonStatus.AVAILABLE,
                List.of(new UnifiedDiffHunk(1, 2, 1, 2, List.of(
                        " public String " + member + "() {",
                        "-    return \"before\";",
                        "+    return \"after\";",
                        " }"))), "");
    }

    private MavenRuntimeDescriptor mavenRuntime(final Path projectRoot) {
        return new MavenRuntimeDescriptor(MavenRuntimeSource.USER_CONFIGURED,
                projectRoot.resolve("fixture-mvn"),
                MavenVersion.parse("3.9.9"), null, projectRoot);
    }

    private JavaRuntimeDescriptor javaRuntime(final Path projectRoot) {
        final Path home = projectRoot.resolve("fixture-jdk8");
        return new JavaRuntimeDescriptor(home, home, "1.8.0-fixture",
                TARGET_JAVA_MAJOR, List.of(), List.of());
    }

    /** Minimal detached query node used only by the report fixture. */
    private record FixtureQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }
}
