package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.util.CommandResolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Final shaded JAR black-box integration tests. */
class PackagedJarCliIT {

    /** Analyzer version supplied by the build. */
    private static final String ANALYZER_VERSION =
            System.getProperty("cia.analyzerVersion");

    /** Dependency Evidence Plugin version supplied by the build. */
    private static final String ARTIFACT_PATH_PLUGIN_VERSION =
            System.getProperty("cia.artifactPathPluginVersion");

    /** Shaded application JAR. */
    private static Path applicationJar;

    /** Java executable. */
    private static Path javaExecutable;

    /** System Maven executable. */
    private static Path maven;

    /** Temporary test root. */
    @TempDir
    private Path temporary;

    @BeforeAll
    static void locateExecutables() throws Exception {
        applicationJar = Path.of(System.getProperty(
                "cia.packagedJar", "target/dependency-analyzer.jar"))
                .toAbsolutePath().normalize();
        javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin", javaExecutableName());
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(List.of(
                        pathLookupCommand(),
                        executableName("mvn"))))
                .redirectErrorStream(true).start();
        final String output = new String(process
                .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        assumeTrue(process.waitFor() == 0
                        && !output.isBlank(),
                "Maven executable unavailable");
        maven = Path.of(output.lines()
                .findFirst().orElseThrow());
    }

    @Test
    void jarHasManifestRuntimeResourcesAndUnifiedHelp()
            throws Exception {
        assertThat(applicationJar).isRegularFile();
        try (JarFile jar = new JarFile(
                applicationJar.toFile())) {
            assertThat(jar.getManifest()
                    .getMainAttributes()
                    .getValue("Main-Class"))
                    .isEqualTo(DependencyAnalyzerCli.class
                            .getName());
            assertThat(jar.getEntry(
                    "maven/apache-maven-3.6.3-bin.zip"))
                    .isNotNull();
            assertThat(jar.getEntry(
                    "maven/apache-maven-3.6.3-bin.zip.sha512"))
                    .isNull();
            assertThat(jar.getEntry("maven/LICENSE"))
                    .isNotNull();
            assertThat(jar.getEntry("maven/NOTICE"))
                    .isNotNull();
            final String dependencyRepository =
                    "maven/plugin-repositories/"
                            + "maven-dependency-plugin-3.6.1-"
                            + "repository.zip";
            final String artifactPathRepository =
                    "maven/plugin-repositories/"
                            + "dependency-analyzer-artifact-path-"
                            + "maven-plugin-" + ARTIFACT_PATH_PLUGIN_VERSION
                            + "-repository.zip";
            assertThat(jar.getEntry(dependencyRepository)).isNotNull();
            assertThat(jar.getEntry(artifactPathRepository)).isNotNull();
            assertThat(jar.getEntry(
                    "maven/dependency-plugin/LICENSE"))
                    .isNotNull();
            assertThat(jar.getEntry(
                    "maven/dependency-plugin/NOTICE"))
                    .isNotNull();
            assertThat(jar.getEntry(
                    "maven/dependency-plugin/DEPENDENCIES"))
                    .isNotNull();
            assertThat(jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith(
                            "maven/plugin-repositories/"))
                    .filter(name -> name.endsWith("-repository.zip"))
                    .toList()).containsExactlyInAnyOrder(
                    dependencyRepository, artifactPathRepository);
            assertThat(jar.getEntry("maven/artifact-path-plugin/"))
                    .isNull();
            assertThat(jar.getEntry(
                    "licenses/vineflower-LICENSE.txt"))
                    .isNotNull();
            assertThat(jar.getEntry(
                    "licenses/wala-LICENSE.txt"))
                    .isNotNull();
            assertThat(jar.getEntry(
                    "org/jetbrains/java/decompiler/api/"
                            + "Decompiler.class"))
                    .isNotNull();
            assertThat(jar.getEntry("io/github/dependencyanalysis/models/"
                    + "jdk/JdkModels.class")).isNotNull();
            assertThat(jar.getEntry("io/github/dependencyanalysis/models/"
                    + "jdk8/Jdk8Models.class")).isNotNull();
            assertThat(jar.getEntry("io/github/dependencyanalysis/models/"
                    + "jdk8/jdk8-models.tsv")).isNotNull();
            assertThat(jar.getEntry("io/github/dependencyanalysis/callgraph/"
                    + "engine/ModuleCallGraphEngine.class")).isNotNull();
            assertThat(jar.getEntry("io/github/dependencyanalysis/callgraph/"
                    + "strategy/CallGraphAlgorithm.class")).isNotNull();
            assertThat(jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith(
                            "io/github/dependencyanalysis/callgraph/"))
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> name.substring(
                            "io/github/dependencyanalysis/callgraph/".length())
                            .indexOf('/') < 0)
                    .filter(name -> !name.endsWith("package-info.class"))
                    .toList()).isEmpty();
            assertThat(jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.startsWith(
                            "io/github/dependencyanalysis/callgraph/"))
                    .filter(name -> name.contains("Rta")
                            || name.contains("ZeroCfa")
                            || name.contains("OptimizedZeroOneCfa"))
                    .toList()).isEmpty();
            assertThat(jar.getEntry("maven/artifact-path-plugin/"
                    + "dependency-analyzer-artifact-path-"
                    + "maven-plugin-1.0.0.jar")).isNull();
        }

        final ProcessResult rootHelp = runJar("--help");
        final ProcessResult impactHelp = runJar(
                "impact", "--help");
        final ProcessResult treeHelp = runJar(
                "tree", "--help");
        final ProcessResult treeAnalyzeHelp = runJar(
                "tree", "analyze", "--help");
        final ProcessResult treeDiffHelp = runJar(
                "tree", "diff", "--help");
        final ProcessResult version = runJar("--version");
        final ProcessResult oldRoot = runJar(
                "--baseline", "HEAD",
                "--output", "report.html");
        final ProcessResult oldProject = runJar(
                "impact", "--project", ".",
                "--baseline", "HEAD",
                "--output", "report.html");
        final ProcessResult oldRepository = runJar(
                "tree", "--repository", ".",
                "--output", "report");

        assertThat(rootHelp.exitCode).isZero();
        assertThat(rootHelp.output)
                .contains("-m, --maven")
                .contains("-j, --java-home")
                .contains("-c, --config-dir")
                .contains("-a, --maven-arg");
        assertThat(impactHelp.exitCode).isZero();
        assertThat(impactHelp.output)
                .contains("-p, --path")
                .contains("-b, --baseline")
                .contains("-t, --target")
                .contains("-o, --output")
                .contains("-f, --format")
                .contains("-k, --include-change-kinds")
                .contains("--jdk-model")
                .doesNotContain("--result-refinement-algorithms")
                .doesNotContain(
                        "--experimental-bytecode-semantic-comparison")
                .contains("--call-graph-timeout-seconds");
        assertThat(impactHelp.output.replaceAll("\\s+", " "))
                .contains("Call Graph algorithm: cha or k-obj "
                        + "(experimental); default: cha.");
        assertThat(treeHelp.exitCode).isZero();
        assertThat(treeHelp.output)
                .contains("analyze")
                .contains("diff");
        assertThat(treeAnalyzeHelp.exitCode).isZero();
        assertThat(treeAnalyzeHelp.output)
                .contains("-p, --path")
                .contains("-r, --ref")
                .contains("-o, --output")
                .contains("-s, --scopes")
                .contains("-d, --dependency-plugin-version");
        assertThat(treeDiffHelp.exitCode).isZero();
        assertThat(treeDiffHelp.output)
                .contains("-p, --path")
                .contains("-b, --baseline")
                .contains("-t, --target")
                .contains("-o, --output")
                .doesNotContain("--ref");
        assertThat(version.exitCode).isZero();
        assertThat(version.output)
                .contains(ANALYZER_VERSION);
        assertThat(oldRoot.exitCode).isEqualTo(1);
        assertThat(oldProject.exitCode).isEqualTo(1);
        assertThat(oldProject.output)
                .contains("Unknown options: '--project', '.'");
        assertThat(oldRepository.exitCode).isEqualTo(1);
        assertThat(oldRepository.output)
                .contains("Unknown options: '--repository', '.'");
    }

    @Test
    void jarRunsRealTreeScenarios()
            throws Exception {
        final Path repository = createRepository(
                temporary.resolve("real-repository"),
                true);
        final Path treeReport = temporary.resolve(
                "tree-report");
        final Path scopedReport = temporary.resolve(
                "scoped-tree-report");
        final Path diffReport = temporary.resolve(
                "tree-diff-report");

        final ProcessResult tree = runJar(
                "tree", "analyze", "-m", maven.toString(),
                "-c", temporary.resolve("tree-config")
                        .toString(),
                "-p", repository.toString(),
                "-o", treeReport.toString());
        final ProcessResult scoped = runJar(
                "-m", maven.toString(), "tree", "analyze",
                "-p", repository.resolve("module")
                        .toString(),
                "-o", scopedReport.toString(),
                "-d", "3.6.1");
        final ProcessResult diff = runJar(
                "tree", "diff", "-m", maven.toString(),
                "-c", temporary.resolve("tree-diff-config").toString(),
                "-p", repository.toString(), "-b", "HEAD",
                "-o", diffReport.toString(), "-d", "3.6.1");

        assertThat(tree.exitCode).as(tree.output).isZero();
        assertThat(tree.output)
                .doesNotContain("evidence is incomplete")
                .doesNotContain("INCOMPLETE_EVIDENCE");
        assertThat(treeReport.resolve("index.html"))
                .content().contains("SUCCESS")
                .contains("<th>analysis path</th>"
                        + "<td><code>.</code></td>");
        final Path treeReactorPage = onlyReactorPagePath(treeReport);
        assertThat(Files.readString(treeReactorPage))
                .contains("FULL_REACTOR")
                .contains("test:module:jar:1")
                .contains("test:unrelated:jar:1");
        assertThat(readReactorShards(treeReactorPage))
                .contains("test:library:jar:1");
        HtmlReportUsabilityVerifier.verifyTree(treeReport);
        assertThat(scoped.exitCode)
                .as(scoped.output).isZero();
        assertThat(scopedReport.resolve("index.html"))
                .content().contains("module")
                .contains("SUCCESS");
        final Path scopedReactorPage = onlyReactorPagePath(scopedReport);
        final String scopedPage = Files.readString(scopedReactorPage);
        assertThat(scopedPage)
                .contains("SINGLE_MODULE")
                .contains("test:module:jar:1")
                .contains("data-combobox=\"module-selector\"")
                .doesNotContain("test:root:pom:1</h2>")
                .doesNotContain("test:unrelated:jar:1");
        assertThat(readReactorShards(scopedReactorPage))
                .contains("test:library:jar:1")
                .doesNotContain("test:unrelated:jar:1");
        HtmlReportUsabilityVerifier.verifyTree(scopedReport);
        assertThat(diff.exitCode).as(diff.output).isZero();
        assertThat(diffReport.resolve("index.html")).content()
                .contains("Dependency Tree Diff")
                .contains("SUCCESS")
                .contains("Current workspace");
        assertThat(readOnlyTreeDiffReactorPage(diffReport))
                .contains("tree-diff-report")
                .contains("Dependency tree comparison")
                .contains("<th scope=\"col\">Actions</th>");
        HtmlReportUsabilityVerifier.verifyTree(diffReport);
    }

    private String readOnlyTreeDiffReactorPage(final Path report)
            throws Exception {
        final Path reactors = report.resolve(
                "tree-diff-report/reactors");
        try (java.util.stream.Stream<Path> files = Files.list(reactors)) {
            final Path page = files.filter(path -> path.getFileName()
                            .toString().endsWith(".html"))
                    .findFirst().orElseThrow();
            return Files.readString(page);
        }
    }

    @Test
    void jarReportsRealProjectReactorAndExternalClassConflicts()
            throws Exception {
        final Path repository = createClassConflictRepository();
        final Path report = temporary.resolve("class-conflict-report");

        final ProcessResult result = runJar(
                "-v", "tree", "analyze", "-m", maven.toString(),
                "-c", temporary.resolve("class-conflict-config").toString(),
                "-p", repository.toString(), "-o", report.toString());

        assertThat(result.exitCode).as(result.output).isZero();
        assertThat(result.output)
                .contains("[analysis][class-scan]")
                .contains("classConflicts=3");
        assertThat(report.resolve("index.html")).content()
                .contains("Class conflicts</th>")
                .contains("High-risk class conflicts</th>");
        final String page = readOnlyReactorPage(report);
        assertThat(page)
                .contains("<h2>Module 分析</h2>")
                .doesNotContain("fixture.conflict.ReactorHigh")
                .doesNotContain(repository.toString())
                .doesNotContain(temporary.resolve(
                        "class-conflict-artifacts").toString());
        final Path reactorPage = onlyReactorPagePath(report);
        final String data = readReactorShards(reactorPage);
        assertThat(data)
                .contains("fixture.conflict.ReactorHigh")
                .contains("fixture.conflict.ExternalLow")
                .contains("fixture.conflict.ExternalHigh")
                .contains("\"risk\":\"LOW\"")
                .contains("\"risk\":\"HIGH\"")
                .contains("PROJECT — test:application:jar:1")
                .contains("REACTOR_DEPENDENCY — test:library:jar:1")
                .contains("DEPENDENCY — fixture.external:conflict-a:jar:1")
                .contains("DEPENDENCY — fixture.external:conflict-b:jar:1")
                .doesNotContain(repository.toString())
                .doesNotContain(temporary.resolve(
                        "class-conflict-artifacts").toString());
        final Path dataDirectory = reactorPage.resolveSibling(
                reactorPage.getFileName().toString().replace(
                        ".html", "-data"));
        try (java.util.stream.Stream<Path> shards =
                     Files.list(dataDirectory)) {
            final List<Path> files = shards.toList();
            assertThat(files).anySatisfy(path -> assertThat(path
                            .getFileName().toString())
                    .startsWith("class-conflicts-"));
            assertThat(files).anySatisfy(path -> assertThat(path)
                    .content().contains("sourceCode", "ExternalLow"));
            assertThat(files).allSatisfy(path -> assertThat(path)
                    .content().doesNotContain(repository.toString()));
        }
        HtmlReportUsabilityVerifier.verifyTree(report);
    }

    @Test
    void jarRunsRealJdk8ImpactScenario()
            throws Exception {
        final String jdk8Home = System.getenv(
                "TEST_JDK8_HOME");
        assertThat(jdk8Home)
                .as("TEST_JDK8_HOME")
                .isNotBlank();
        final Path repository = createRepository(
                temporary.resolve("impact-repository"),
                true);
        final Path impactReport = temporary.resolve(
                "impact-report");

        final ProcessResult impact = runJar(
                "-v",
                "-m", maven.toString(),
                "-j", jdk8Home,
                "-c", temporary.resolve("impact-config")
                        .toString(),
                "impact", "-p", repository.toString(),
                "-b", "HEAD", "-t", "HEAD",
                "-o", impactReport.toString(),
                "-f", "html");

        assertThat(impact.exitCode)
                .as(impact.output).isZero();
        assertThat(impact.output)
                .doesNotContain("got NEW")
                .contains("dependencyEvidencePlugin="
                        + ARTIFACT_PATH_PLUGIN_VERSION)
                .contains("Dependency Evidence Plugin implementation="
                        + "dependency-evidence-v3");
        assertThat(impactReport.resolve("index.html")).content()
                .contains("Impact Analysis Report")
                .contains("<th>Algorithm</th><td>cha</td>")
                .contains("<th>JDK method model</th><td>none</td>")
                .contains("<th>Method body equivalence order</th><td>"
                        + "Decompiled Java first; normalized SSA on miss</td>")
                .doesNotContain("Result refinement algorithms")
                .contains("Preflight")
                .contains("impact.java-runtime");
        HtmlReportUsabilityVerifier.verifyImpact(
                impactReport.resolve("index.html"));
    }

    @Test
    void jarRunsKObjChangedDependencySmoke()
            throws Exception {
        final String jdk8Home = System.getenv("TEST_JDK8_HOME");
        assertThat(jdk8Home).as("TEST_JDK8_HOME").isNotBlank();
        final Path repository = createChangedDependencyRepository();
        final Path report = temporary.resolve("k-obj-smoke-report");

        final ProcessResult result = runJar(
                "-v", "-m", maven.toString(), "-j", jdk8Home,
                "-c", temporary.resolve("k-obj-smoke-config").toString(),
                "impact", "-p", repository.toString(),
                "-b", "HEAD~1", "-t", "HEAD",
                "-o", report.toString(), "-f", "html",
                "--call-graph-algorithm", "k-obj");

        assertThat(result.exitCode).as(result.output).isZero();
        assertThat(result.output)
                .contains("algorithm=k-obj (experimental)");
        assertThat(report.resolve("index.html")).content()
                .contains("Impact Analysis Report")
                .contains("<th>Algorithm</th><td>k-obj (experimental)</td>")
                .contains("<th>JDK method model</th><td>jdk8</td>")
                .contains("k-Object (experimental)");
    }

    @Test
    void jarRejectsRemovedResultRefinementOption() throws Exception {
        final ProcessResult result = runJar(
                "impact", "-b", "HEAD", "-o",
                temporary.resolve("removed-option.html").toString(),
                "--result-refinement-algorithms", "none");

        assertThat(result.exitCode).as(result.output).isEqualTo(1);
        assertThat(result.output).contains("Unknown option");
    }

    @Test
    void jarReportsManagedAndMultiVersionDependencyData()
            throws Exception {
        final Path repository = createManagedConflictRepository();
        final Path fakeMaven = fakeManagedConflictMaven();
        final Path report = temporary.resolve(
                "managed-conflict-report");

        final ProcessResult result = runJar(
                "tree", "analyze", "-m", fakeMaven.toString(),
                "-c", temporary.resolve("managed-config")
                        .toString(),
                "-p", repository.toString(),
                "-o", report.toString());

        assertThat(result.exitCode).as(result.output).isZero();
        final String index = Files.readString(
                report.resolve("index.html"));
        assertThat(index)
                .contains("<h2>Metadata</h2>")
                .contains("<th>Field</th><th>Value</th>")
                .contains("<h2>Summary</h2>")
                .contains("<th>Status</th><th>Progress</th>")
                .contains("<th>Internal conflicts</th>")
                .contains("<th>Multi-version dependencies</th>")
                .contains("<h2>Reactors</h2>");
        final String page = readOnlyReactorPage(report);
        assertThat(page)
                .contains("<h2>跨模块依赖分析</h2>")
                .contains("<h2>Module 分析</h2>")
                .contains("role=\"combobox\"")
                .contains("data-combobox=\"dependency-filter\"")
                .contains("data-combobox=\"module-selector\"")
                .contains("Dependency chain")
                .contains("Original version")
                .contains("Resolved version")
                .contains("Resolution source")
                .contains("test:module-a:jar:1")
                .contains("test:module-b:jar:1")
                .doesNotContain("DEPENDENCY_PATH")
                .doesNotContain("<pre class=\"dependency-tree\">")
                .doesNotContain("<details");
        assertThat(readReactorShards(onlyReactorPagePath(report)))
                .contains("\"schemaVersion\":2")
                .contains("module-dependency-catalog")
                .contains("Dependency management")
                .contains("Duplicate mediation")
                .contains("requested 1 → effective 2")
                .contains("version managed from 1")
                .contains("omitted for duplicate")
                .contains("fixture:duplicate-only:jar:1:compile")
                .contains("fixture:middle:jar:1:compile")
                .contains("fixture:shared:jar:2:compile")
                .contains("fixture:shared:jar:3:compile")
                .contains("duplicate")
                .doesNotContain("DEPENDENCY_PATH")
                .doesNotContain("DEPENDENCY_MANAGEMENT")
                .doesNotContain("internal-conflicts")
                .doesNotContain("<details")
                .doesNotContain(">root</button>")
                .doesNotContain("<th>Status</th><th>Reason</th>")
                .doesNotContain("data-dependency=\""
                        + "fixture:duplicate-only:jar:\"");
    }

    @Test
    void treePreflightFailurePrintsThreeStageSummary()
            throws Exception {
        final Path output = temporary.resolve(
                "blocked-tree-report");

        final ProcessResult result = runJar(
                "tree", "analyze", "-p", temporary.resolve(
                        "missing").toString(),
                "-o", output.toString());

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output)
                .contains("Stage 1/3: Preflight")
                .contains("Stage 2/3: Analysis")
                .contains("[analysis][summary][-]"
                        + " Stage 2/3: Analysis; status=SKIPPED;"
                        + " reason=command preflight failed")
                .contains("[summary][result]"
                        + "[-] Stage 3/3: Summary; status=FAILED;"
                        + " report=NOT_GENERATED");
        assertThat(output).doesNotExist();
    }

    @Test
    void treeFailureCompletesReportWithAggregatedIssue()
            throws Exception {
        assumeFalse(System.getProperty("os.name", "")
                        .toLowerCase(Locale.ROOT)
                        .contains("win"),
                "POSIX fake Maven test");
        final Path repository = createRepository(
                temporary.resolve("failing-repository"),
                false);
        final Path output = temporary.resolve(
                "failed-tree-report");
        final Path fakeMaven = fakeFailingMaven();

        final ProcessResult result = runJar(
                "tree", "analyze", "-m", fakeMaven.toString(),
                "-p", repository.toString(),
                "-o", output.toString(),
                "-d", "3.6.1");

        assertThat(result.exitCode).isEqualTo(2);
        assertThat(result.output)
                .contains("Stage 2/3: Analysis")
                .contains("[analysis][summary]"
                        + "[-] Analysis completed;"
                        + " status=COMPLETED_WITH_ISSUES; issues=1")
                .contains("status=COMPLETED_WITH_ISSUES")
                .contains("report=" + output.toAbsolutePath()
                        .normalize());
        assertThat(output.resolve("index.html"))
                .content().contains("COMPLETED_WITH_ISSUES");
        assertThat(result.output)
                .containsOnlyOnce("failure-line-001")
                .containsOnlyOnce("failure-line-105");
        assertThat(readOnlyReactorPage(output))
                .contains("exitCode=1")
                .doesNotContain("failure-line-");
    }

    @Test
    void allCommandsForwardNativeLogsAtEveryVerbosity() throws Exception {
        assumeFalse(System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win"), "POSIX fixture");
        final Path repository = createRepository(
                temporary.resolve("logging-repository"), false);
        final Path fakeMaven = fakeFailingMaven();
        for (String verbosity : List.of("", "-v", "-vv")) {
            for (String command : List.of("impact", "analyze", "diff")) {
                final Path output = temporary.resolve(
                        "logging-" + command + verbosity + ".html");
                final List<String> arguments = new ArrayList<>();
                if (!verbosity.isEmpty()) {
                    arguments.add(verbosity);
                }
                if (!command.equals("impact")) {
                    arguments.add("tree");
                }
                arguments.add(command);
                arguments.addAll(List.of("-m", fakeMaven.toString(),
                        "-p", repository.toString(), "-o", output.toString()));
                if (!command.equals("analyze")) {
                    arguments.addAll(List.of("-b", "HEAD"));
                }
                if (command.equals("impact")) {
                    arguments.addAll(List.of("-j",
                            System.getenv("TEST_JDK8_HOME")));
                }
                final ProcessResult result = runJar(
                        arguments.toArray(String[]::new));
                assertThat(result.exitCode).as(command + verbosity).isNotZero();
                assertThat(result.output).as(command + verbosity)
                        .contains("failure-line-001", "failure-line-105");
                assertThat(result.output).contains("native-debug="
                        + !verbosity.isEmpty());
            }
        }
    }

    @Test
    void runtimeLogsUseStderrAndMetricsRequireTrace() throws Exception {
        final Path debugOutput = temporary.resolve("debug-impact-report");
        final Path traceOutput = temporary.resolve("trace-impact-report");

        final SplitProcessResult debug = runJarSplit(
                "-v", "impact", "-b", "HEAD",
                "-o", debugOutput.toString(),
                "--call-graph-timeout-seconds", "-1");
        final SplitProcessResult trace = runJarSplit(
                "-vv", "impact", "-b", "HEAD",
                "-o", traceOutput.toString(),
                "--call-graph-timeout-seconds", "-1");

        assertThat(debug.exitCode).isEqualTo(1);
        assertThat(debug.stdout).isEmpty();
        assertThat(debug.stderr)
                .contains("[ERROR][preflight][-][-]")
                .doesNotContain("[runtime-metrics]");
        assertThat(trace.exitCode).isEqualTo(1);
        assertThat(trace.stdout).isEmpty();
        assertThat(trace.stderr)
                .contains("[TRACE][runtime-metrics][heap][-]"
                        + " Runtime metrics snapshot; sample=1")
                .contains("heapUsedMiB=")
                .doesNotContain("heartbeat", "HEARTBEAT");
        assertThat(trace.stderr.lines()).allMatch(line -> line.matches(
                "^\\[[^]]+]\\[(TRACE|DEBUG|INFO|WARN|ERROR)]"
                        + "\\[[^]]+]\\[[^]]+]\\[[^]]+] .*$"));
        assertThat(trace.stderr.lines()).allMatch(line -> !line.matches(
                "^\\[[^]]+]\\[[^]]+]\\[[^]]+]\\[[^]]+]"
                        + "\\[[^]]*(command|side|path|scope|scopeId|"
                        + "progress|status|decision|elapsedMs|sample|"
                        + "reactors|modules|issues|issue|report|core|max|"
                        + "size|active|queued|completed|submitted|shutdown|"
                        + "terminated|heapUsedMiB|heapCommittedMiB|"
                        + "heapMaxMiB)=.*"));
    }

    @Test
    void containerWithoutEntryPomKeepsLastPublishedReport()
            throws Exception {
        final Path repository = createIndependentReactors();
        final Path output = temporary.resolve(
                "preserved-report");
        Files.createDirectories(output);
        final Path previous = output.resolve("index.html");
        Files.writeString(previous, "previous-report");

        final ProcessResult result = runJar(
                "-m", maven.toString(),
                "tree", "analyze", "-p", repository.toString(),
                "-o", output.toString(),
                "-d", "3.6.1");

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.output)
                .contains("Analysis POM is unavailable")
                .contains("report=NOT_GENERATED");
        assertThat(previous).content().isEqualTo("previous-report");
    }

    private Path createRepository(
            final Path repository,
            final boolean withModule) throws Exception {
        Files.createDirectories(repository);
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        final String modules = withModule
                ? "<modules><module>library</module>"
                + "<module>module</module>"
                + "<module>unrelated</module></modules>"
                : "";
        writePom(repository.resolve("pom.xml"),
                "root", "pom", modules);
        if (withModule) {
            Files.createDirectories(repository
                    .resolve("module"));
            Files.createDirectories(repository
                    .resolve("library"));
            Files.createDirectories(repository
                    .resolve("unrelated"));
            Files.writeString(repository.resolve(
                    "library/pom.xml"), childPom(
                    "library", ""), StandardCharsets.UTF_8);
            Files.writeString(repository.resolve(
                    "module/pom.xml"), childPom(
                    "module", """
                            <dependencies><dependency>
                              <groupId>test</groupId>
                              <artifactId>library</artifactId>
                              <version>1</version>
                            </dependency></dependencies>
                            """), StandardCharsets.UTF_8);
            Files.writeString(repository.resolve(
                    "unrelated/pom.xml"), childPom(
                    "unrelated", ""), StandardCharsets.UTF_8);
        }
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
        return repository;
    }

    private Path createChangedDependencyRepository() throws Exception {
        final Path repository = temporary.resolve(
                "k-obj-changed-dependency-repository");
        final Path artifactRepository = temporary.resolve(
                "k-obj-artifact-repository");
        final Path applicationSource = repository.resolve(
                "src/main/java/fixture/application/Caller.java");
        installChangedDependencyArtifact(artifactRepository, "1", 1);
        installChangedDependencyArtifact(artifactRepository, "2", 2);
        Files.createDirectories(applicationSource.getParent());
        git(repository, "init");
        git(repository, "config", "user.email", "test@example.com");
        git(repository, "config", "user.name", "Test");
        final String baselineRootPom = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId>
                  <artifactId>application</artifactId>
                  <version>1</version>
                  <properties>
                    <maven.compiler.source>8</maven.compiler.source>
                    <maven.compiler.target>8</maven.compiler.target>
                    <changed.version>1</changed.version>
                  </properties>
                  <repositories><repository>
                    <id>changed-dependency-fixture</id>
                    <url>%s</url>
                  </repository></repositories>
                  <dependencies><dependency>
                    <groupId>fixture.external</groupId>
                    <artifactId>changed-library</artifactId>
                    <version>${changed.version}</version>
                  </dependency></dependencies>
                </project>
                """.formatted(artifactRepository.toUri().toASCIIString());
        Files.writeString(repository.resolve("pom.xml"), baselineRootPom,
                StandardCharsets.UTF_8);
        Files.writeString(applicationSource, """
                package fixture.application;
                import fixture.library.ChangedDependency;
                public class Caller {
                    public int call() {
                        return new ChangedDependency().value();
                    }
                }
                """, StandardCharsets.UTF_8);
        git(repository, "add", ".");
        git(repository, "commit", "-m", "baseline dependency");
        Files.writeString(repository.resolve("pom.xml"),
                baselineRootPom.replace(
                        "<changed.version>1</changed.version>",
                        "<changed.version>2</changed.version>"),
                StandardCharsets.UTF_8);
        git(repository, "add", ".");
        git(repository, "commit", "-m", "change dependency body");
        return repository;
    }

    private Path createClassConflictRepository() throws Exception {
        final Path repository = temporary.resolve(
                "class-conflict-repository");
        final Path artifactRepository = temporary.resolve(
                "class-conflict-artifacts");
        final byte[] low = compileConflictClass(
                "external-low", "ExternalLow", 7);
        installConflictArtifact(artifactRepository, "conflict-a", Map.of(
                "fixture/conflict/ExternalLow.class", low,
                "fixture/conflict/ExternalHigh.class", compileConflictClass(
                        "external-high-a", "ExternalHigh", 1)));
        installConflictArtifact(artifactRepository, "conflict-b", Map.of(
                "fixture/conflict/ExternalLow.class", low,
                "fixture/conflict/ExternalHigh.class", compileConflictClass(
                        "external-high-b", "ExternalHigh", 2)));

        Files.createDirectories(repository.resolve(
                "library/src/main/java/fixture/conflict"));
        Files.createDirectories(repository.resolve(
                "application/src/main/java/fixture/conflict"));
        git(repository, "init");
        git(repository, "config", "user.email", "test@example.com");
        git(repository, "config", "user.name", "Test");
        Files.writeString(repository.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><packaging>pom</packaging>
                  <properties><maven.compiler.source>8</maven.compiler.source>
                    <maven.compiler.target>8</maven.compiler.target>
                  </properties>
                  <repositories><repository><id>class-conflict-fixture</id>
                    <url>%s</url></repository></repositories>
                  <modules><module>library</module>
                    <module>application</module></modules>
                </project>
                """.formatted(artifactRepository.toUri().toASCIIString()),
                StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("library/pom.xml"),
                childPom("library", ""), StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("application/pom.xml"),
                childPom("application", """
                        <dependencies>
                          <dependency><groupId>test</groupId>
                            <artifactId>library</artifactId><version>1</version>
                          </dependency>
                          <dependency><groupId>fixture.external</groupId>
                            <artifactId>conflict-a</artifactId><version>1</version>
                          </dependency>
                          <dependency><groupId>fixture.external</groupId>
                            <artifactId>conflict-b</artifactId><version>1</version>
                          </dependency>
                        </dependencies>
                        """), StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(
                "library/src/main/java/fixture/conflict/ReactorHigh.java"),
                conflictSource("ReactorHigh", 1), StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(
                "application/src/main/java/fixture/conflict/ReactorHigh.java"),
                conflictSource("ReactorHigh", 2), StandardCharsets.UTF_8);
        git(repository, "add", ".");
        git(repository, "commit", "-m", "class conflict fixture");
        return repository;
    }

    private byte[] compileConflictClass(
            final String fixture,
            final String className,
            final int value) throws Exception {
        final Path source = temporary.resolve(fixture
                + "/src/fixture/conflict/" + className + ".java");
        final Path classes = temporary.resolve(fixture + "/classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, conflictSource(className, value),
                StandardCharsets.UTF_8);
        final int result = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "8", "-d",
                classes.toString(), source.toString());
        if (result != 0) {
            throw new IllegalStateException(
                    "Unable to compile conflict fixture " + fixture);
        }
        return Files.readAllBytes(classes.resolve(
                "fixture/conflict/" + className + ".class"));
    }

    private String conflictSource(
            final String className,
            final int value) {
        return "package fixture.conflict; public class " + className
                + " { public int value() { return " + value + "; } }";
    }

    private void installConflictArtifact(
            final Path repository,
            final String artifact,
            final Map<String, byte[]> classes) throws Exception {
        final Path directory = repository.resolve(
                "fixture/external/" + artifact + "/1");
        Files.createDirectories(directory);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(
                directory.resolve(artifact + "-1.jar")))) {
            for (Map.Entry<String, byte[]> value : classes.entrySet()) {
                jar.putNextEntry(new JarEntry(value.getKey()));
                jar.write(value.getValue());
                jar.closeEntry();
            }
        }
        Files.writeString(directory.resolve(artifact + "-1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.external</groupId>
                  <artifactId>%s</artifactId><version>1</version>
                </project>
                """.formatted(artifact), StandardCharsets.UTF_8);
    }

    private void installChangedDependencyArtifact(
            final Path repository,
            final String version,
            final int value) throws Exception {
        final Path source = temporary.resolve(
                "k-obj-artifact-" + version
                        + "/src/fixture/library/ChangedDependency.java");
        final Path classes = temporary.resolve(
                "k-obj-artifact-" + version + "/classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, """
                package fixture.library;
                public class ChangedDependency {
                    public int value() { return %d; }
                }
                """.formatted(value), StandardCharsets.UTF_8);
        final int compileExit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "8", "-d",
                classes.toString(), source.toString());
        if (compileExit != 0) {
            throw new IllegalStateException(
                    "Unable to compile changed dependency " + version);
        }
        final Path directory = repository.resolve(
                "fixture/external/changed-library/" + version);
        Files.createDirectories(directory);
        final Path jarPath = directory.resolve(
                "changed-library-" + version + ".jar");
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry(
                    "fixture/library/ChangedDependency.class"));
            Files.copy(classes.resolve(
                    "fixture/library/ChangedDependency.class"), jar);
            jar.closeEntry();
        }
        Files.writeString(directory.resolve(
                "changed-library-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture.external</groupId>
                  <artifactId>changed-library</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(version), StandardCharsets.UTF_8);
    }

    private Path createManagedConflictRepository()
            throws Exception {
        final Path repository = temporary.resolve(
                "managed-conflict-repository");
        Files.createDirectories(repository.resolve("module-a"));
        Files.createDirectories(repository.resolve("module-b"));
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        writePom(repository.resolve("pom.xml"),
                "root", "pom", "<modules>"
                        + "<module>module-a</module>"
                        + "<module>module-b</module>"
                        + "</modules>");
        Files.writeString(repository.resolve(
                "module-a/pom.xml"), childPom(
                "module-a", ""), StandardCharsets.UTF_8);
        Files.writeString(repository.resolve(
                "module-b/pom.xml"), childPom(
                "module-b", ""), StandardCharsets.UTF_8);
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
        return repository;
    }

    private String childPom(
            final String artifact,
            final String body) {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>test</groupId>
                    <artifactId>root</artifactId><version>1</version>
                  </parent>
                  <artifactId>%s</artifactId>%s
                </project>
                """.formatted(artifact, body);
    }

    private Path createIndependentReactors()
            throws Exception {
        final Path repository = temporary.resolve(
                "independent-reactors");
        Files.createDirectories(repository.resolve("a"));
        Files.createDirectories(repository.resolve("b"));
        git(repository, "init");
        git(repository, "config", "user.email",
                "test@example.com");
        git(repository, "config", "user.name", "Test");
        writePom(repository.resolve("a/pom.xml"),
                "a", "jar", "");
        writePom(repository.resolve("b/pom.xml"),
                "b", "jar", "");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "initial");
        return repository;
    }

    private void writePom(
            final Path pom,
            final String artifact,
            final String packaging,
            final String modules) throws Exception {
        Files.writeString(pom, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>%s</artifactId>
                  <version>1</version>
                  <packaging>%s</packaging>
                  %s
                </project>
                """.formatted(artifact, packaging, modules),
                StandardCharsets.UTF_8);
    }

    private Path fakeFailingMaven() throws Exception {
        final Path executable = temporary.resolve(
                "failing-mvn");
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "Apache Maven 3.9.9"
                  exit 0
                fi
                debug=false
                for argument in "$@"; do
                  if [ "$argument" = "-X" ]; then debug=true; fi
                done
                echo "native-debug=$debug"
                line=1
                while [ "$line" -le 105 ]; do
                  printf 'failure-line-%03d\\n' "$line"
                  line=$((line + 1))
                done
                exit 1
                """, StandardCharsets.UTF_8);
        assertThat(executable.toFile()
                .setExecutable(true)).isTrue();
        return executable;
    }

    private Path fakeManagedConflictMaven() throws Exception {
        final Path executable = temporary.resolve(
                "managed-conflict-mvn");
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "Apache Maven 3.9.9"
                  exit 0
                fi
                pom=""
                output=""
                classpath=""
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    -f)
                      shift
                      pom="$1"
                      ;;
                    -DoutputFile=*)
                      output="${1#-DoutputFile=}"
                      ;;
                    -Dcia.classpathEvidenceDirectory=*)
                      classpath="${1#-Dcia.classpathEvidenceDirectory=}"
                      ;;
                  esac
                  shift
                done
                base="$(dirname "$pom")"
                mkdir -p "$base/module-a/$(dirname "$output")"
                mkdir -p "$base/module-b/$(dirname "$output")"
                printf '%s\n' \
                  'test:module-a:jar:1' \
                  '+- fixture:middle:jar:1:compile' \
                  '|  \\- fixture:shared:jar:2:compile (version managed from 1)' \
                  '\\- fixture:shared:jar:2:compile (omitted for duplicate)' \
                  > "$base/module-a/$output"
                printf '%s\n' \
                  'test:module-b:jar:1' \
                  '+- fixture:shared:jar:3:compile' \
                  '+- fixture:duplicate-only:jar:1:compile' \
                  '\\- fixture:duplicate-only:jar:1:compile (omitted for duplicate)' \
                  > "$base/module-b/$output"
                mkdir -p "$classpath"
                for artifact in module-a module-b; do
                  canonical="$(cd "$base/$artifact" && pwd -P)"
                  printf '{"schemaVersion":1,"javaMajor":17,"module":{"groupId":"test","artifactId":"%s","type":"jar","extension":"jar","classifier":"","version":"1","baseVersion":"1"},"moduleDirectory":"%s","entries":[],"issues":[]}\n' \
                    "$artifact" "$canonical" \
                    > "$classpath/classpath-module-$artifact.json"
                done
                exit 0
                """, StandardCharsets.UTF_8);
        assertThat(executable.toFile()
                .setExecutable(true)).isTrue();
        return executable;
    }

    private String readOnlyReactorPage(final Path output)
            throws Exception {
        return Files.readString(onlyReactorPagePath(output));
    }

    private Path onlyReactorPagePath(final Path output)
            throws Exception {
        try (java.util.stream.Stream<Path> pages =
                     Files.list(output.resolve(
                             "dependency-report/reactors"))) {
            return pages.filter(
                            Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".html"))
                    .findFirst().orElseThrow();
        }
    }

    private String readReactorShards(final Path reactorPage)
            throws Exception {
        final String filename = reactorPage.getFileName().toString();
        final Path directory = reactorPage.resolveSibling(
                filename.substring(0,
                        filename.length() - ".html".length()) + "-data");
        final StringBuilder result = new StringBuilder();
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .sorted().toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private ProcessResult runJar(
            final String... arguments) throws Exception {
        final Process process = new ProcessBuilder(
                jarCommand(arguments))
                .redirectErrorStream(true).start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private SplitProcessResult runJarSplit(
            final String... arguments) throws Exception {
        final Process process = new ProcessBuilder(
                jarCommand(arguments)).start();
        final String stdout = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        final String stderr = new String(
                process.getErrorStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new SplitProcessResult(
                process.waitFor(), stdout, stderr);
    }

    private List<String> jarCommand(
            final String... arguments) {
        final List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-jar");
        command.add(applicationJar.toString());
        command.addAll(List.of(arguments));
        return command;
    }

    private void git(
            final Path directory,
            final String... arguments) throws Exception {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(command))
                .directory(directory.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    private static String executableName(
            final String baseName) {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win")
                ? baseName + ".cmd" : baseName;
    }

    private static String javaExecutableName() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win")
                ? "java.exe" : "java";
    }

    private static String pathLookupCommand() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win")
                ? "where" : "which";
    }

    /** Captured black-box process result. */
    private record ProcessResult(
            int exitCode,
            String output) {
    }

    /** Separate process streams. */
    private record SplitProcessResult(
            int exitCode,
            String stdout,
            String stderr) {
    }
}
