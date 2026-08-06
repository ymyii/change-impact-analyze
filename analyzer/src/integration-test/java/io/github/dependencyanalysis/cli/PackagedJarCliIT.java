package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.util.CommandResolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Final shaded JAR black-box integration tests. */
class PackagedJarCliIT {

    /** Analyzer version supplied by the build. */
    private static final String ANALYZER_VERSION =
            System.getProperty("cia.analyzerVersion");

    /** Artifact Path Plugin version supplied by the build. */
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
            assertThat(jar.getEntry("maven/artifact-path-plugin/"
                    + "dependency-analyzer-artifact-path-"
                    + "maven-plugin-1.0.0.jar")).isNull();
        }

        final ProcessResult rootHelp = runJar("--help");
        final ProcessResult impactHelp = runJar(
                "impact", "--help");
        final ProcessResult treeHelp = runJar(
                "tree", "--help");
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
                .contains("--call-graph-timeout-seconds");
        assertThat(treeHelp.exitCode).isZero();
        assertThat(treeHelp.output)
                .contains("-p, --path")
                .contains("-r, --ref")
                .contains("-o, --output")
                .contains("-s, --scopes")
                .contains("-d, --dependency-plugin-version");
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

        final ProcessResult tree = runJar(
                "tree", "-m", maven.toString(),
                "-c", temporary.resolve("tree-config")
                        .toString(),
                "-p", repository.toString(),
                "-o", treeReport.toString());
        final ProcessResult scoped = runJar(
                "-m", maven.toString(), "tree",
                "-p", repository.resolve("module")
                        .toString(),
                "-o", scopedReport.toString(),
                "-d", "3.6.1");

        assertThat(tree.exitCode).as(tree.output).isZero();
        assertThat(tree.output)
                .doesNotContain("evidence is incomplete")
                .doesNotContain("INCOMPLETE_EVIDENCE");
        assertThat(treeReport.resolve("index.html"))
                .content().contains("SUCCESS")
                .contains("<th>analysis path</th>"
                        + "<td><code>.</code></td>");
        assertThat(readOnlyReactorPage(treeReport))
                .contains("FULL_REACTOR")
                .contains("test:module:jar:1")
                .contains("test:library:jar:1")
                .contains("test:unrelated:jar:1");
        assertThat(scoped.exitCode)
                .as(scoped.output).isZero();
        assertThat(scopedReport.resolve("index.html"))
                .content().contains("module")
                .contains("SUCCESS");
        final String scopedPage = readOnlyReactorPage(
                scopedReport);
        assertThat(scopedPage)
                .contains("BOUNDED_MODULE")
                .contains("test:module:jar:1")
                .contains("test:library:jar:1")
                .doesNotContain("test:root:pom:1</h2>")
                .doesNotContain("test:unrelated:jar:1");
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
                "impact.html");

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
                .contains("artifactPathPlugin="
                        + ARTIFACT_PATH_PLUGIN_VERSION)
                .contains("Artifact Path Plugin implementation=graphml-v2");
        assertThat(impactReport).content()
                .contains("Impact Analysis Report")
                .contains("Preflight")
                .contains("impact.java-runtime");
    }

    @Test
    void jarReportsManagedAndCrossModuleVersionEvidence()
            throws Exception {
        final Path repository = createManagedConflictRepository();
        final Path fakeMaven = fakeManagedConflictMaven();
        final Path report = temporary.resolve(
                "managed-conflict-report");

        final ProcessResult result = runJar(
                "tree", "-m", fakeMaven.toString(),
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
                .contains("<th>Cross-module conflicts</th>")
                .contains("<h2>Reactors</h2>");
        final String page = readOnlyReactorPage(report);
        assertThat(page)
                .contains("<h3>跨模块依赖冲突</h3>")
                .contains("<h2>Module 分析</h2>")
                .contains("<h3>模块内部依赖冲突</h3>")
                .contains("role=\"tablist\"")
                .contains("role=\"tab\"")
                .contains("role=\"tabpanel\"")
                .contains(">module-a</button>")
                .contains(">module-b</button>")
                .contains("DEPENDENCY_PATH")
                .contains("DEPENDENCY_MANAGEMENT")
                .contains("<th>Source</th><th>Dependency chain</th>")
                .contains("<th>Source</th><th>Module</th>")
                .contains("<th>Original version</th><th>Scope</th>")
                .contains("DEPENDENCY_MANAGEMENT</code></td><td></td>")
                .contains("<pre class=\"dependency-tree\">")
                .contains("version managed from 1")
                .contains("omitted for duplicate")
                .contains("+- fixture:duplicate-only:jar:1:compile\n"
                        + "\\- fixture:duplicate-only:jar:1:compile "
                        + "(omitted for duplicate)")
                .contains("test:module-a:jar:1")
                .contains("test:module-b:jar:1")
                .contains("fixture:middle:jar:1:compile")
                .contains("fixture:shared:jar:2:compile")
                .contains("fixture:shared:jar:3:compile")
                .contains("duplicate")
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
                "tree", "-p", temporary.resolve(
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
                "tree", "-m", fakeMaven.toString(),
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
        assertThat(readOnlyReactorPage(output))
                .contains("failure-line-006")
                .contains("failure-line-105")
                .doesNotContain("failure-line-005");
    }

    @Test
    void runtimeLogsUseStderrAndMetricsRequireTrace() throws Exception {
        final Path debugOutput = temporary.resolve("debug-impact.html");
        final Path traceOutput = temporary.resolve("trace-impact.html");

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
                        + "size|active|queued|completed|tasks|shutdown|"
                        + "terminated|heapUsedMiB|heapCommittedMiB|"
                        + "heapMaxMiB)=.*"));
    }

    @Test
    void interruptedJarKeepsLastPublishedCheckpoint()
            throws Exception {
        assumeFalse(System.getProperty("os.name", "")
                        .toLowerCase(Locale.ROOT)
                        .contains("win"),
                "POSIX fake Maven test");
        final Path repository = createIndependentReactors();
        final Path output = temporary.resolve(
                "interrupted-report");
        final Path fakeMaven = fakeBlockingMaven();
        final Path log = temporary.resolve("interrupted.log");
        final List<String> command = jarCommand(
                "-m", fakeMaven.toString(),
                "tree", "-p", repository.toString(),
                "-o", output.toString(),
                "-d", "3.6.1");
        final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        try {
            waitForCheckpoint(output.resolve("index.html"),
                    "<td>1/2</td>", Duration.ofSeconds(20));
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }

        assertThat(output.resolve("index.html"))
                .content().contains("RUNNING")
                .contains("<td>1/2</td>");
        try (java.util.stream.Stream<Path> pages =
                     Files.list(output.resolve(
                             "dependency-report/reactors"))) {
            assertThat(pages.filter(Files::isRegularFile)
                    .count()).isEqualTo(1L);
        }
        assertThat(readOnlyReactorPage(output))
                .contains("test:a:jar:1");
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

    private Path fakeBlockingMaven() throws Exception {
        final Path executable = temporary.resolve(
                "blocking-mvn");
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "Apache Maven 3.9.9"
                  exit 0
                fi
                pom=""
                output=""
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    -f)
                      shift
                      pom="$1"
                      ;;
                    -DoutputFile=*)
                      output="${1#-DoutputFile=}"
                      ;;
                  esac
                  shift
                done
                base="$(dirname "$pom")"
                artifact="$(basename "$base")"
                if [ "$artifact" = "b" ]; then
                  sleep 30
                fi
                mkdir -p "$base/$(dirname "$output")"
                printf 'test:%s:jar:1\n' "$artifact" \
                  > "$base/$output"
                exit 0
                """, StandardCharsets.UTF_8);
        assertThat(executable.toFile()
                .setExecutable(true)).isTrue();
        return executable;
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
                while [ "$#" -gt 0 ]; do
                  case "$1" in
                    -f)
                      shift
                      pom="$1"
                      ;;
                    -DoutputFile=*)
                      output="${1#-DoutputFile=}"
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
                exit 0
                """, StandardCharsets.UTF_8);
        assertThat(executable.toFile()
                .setExecutable(true)).isTrue();
        return executable;
    }

    private void waitForCheckpoint(
            final Path index,
            final String expected,
            final Duration timeout) throws Exception {
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (Files.isRegularFile(index)
                    && Files.readString(index)
                    .contains(expected)) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError(
                "Checkpoint was not published: " + expected);
    }

    private String readOnlyReactorPage(final Path output)
            throws Exception {
        try (java.util.stream.Stream<Path> pages =
                     Files.list(output.resolve(
                             "dependency-report/reactors"))) {
            final Path page = pages.filter(
                            Files::isRegularFile)
                    .findFirst().orElseThrow();
            return Files.readString(page);
        }
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
