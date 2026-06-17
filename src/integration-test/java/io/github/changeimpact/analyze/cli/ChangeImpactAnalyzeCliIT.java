package io.github.changeimpact.analyze.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.junit.jupiter.api.Assumptions
        .assumeTrue;

/**
 * Integration tests for
 * {@link ChangeImpactAnalyzeCli}.
 *
 * <p>Covers negative cases (missing
 * params, invalid paths, non-git repos)
 * and E2E success scenarios with a
 * simple Java 8 single-module Maven
 * project.</p>
 */
class ChangeImpactAnalyzeCliIT {

    /** JUnit temp directory. */
    @TempDir
    private Path tempDir;

    /** Original System.err stream. */
    private PrintStream originalErr;

    /** Captured stderr content. */
    private ByteArrayOutputStream errBuf;

    @BeforeAll
    static void checkGitAndMvn()
            throws Exception {
        final ProcessBuilder gitPb =
                new ProcessBuilder(
                        "git", "--version");
        gitPb.redirectErrorStream(true);
        final Process gitProc =
                gitPb.start();
        final int gitCode =
                gitProc.waitFor();
        assumeTrue(gitCode == 0,
                "Git not available");

        final ProcessBuilder mvnPb =
                new ProcessBuilder(
                        "mvn", "--version");
        mvnPb.redirectErrorStream(true);
        final Process mvnProc =
                mvnPb.start();
        final int mvnCode =
                mvnProc.waitFor();
        assumeTrue(mvnCode == 0,
                "Maven not available");
    }

    @BeforeEach
    void captureStderr() {
        originalErr = System.err;
        errBuf = new ByteArrayOutputStream();
        System.setErr(
                new PrintStream(errBuf));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(originalErr);
    }

    // ---- Negative cases ----

    @Test
    void missingRequiredParamsFails() {
        final CommandLine cmd =
                newCli();
        final int code =
                cmd.execute(new String[]{});
        assertThat(code).isNotZero();
    }

    @Test
    void projectNotExistsReturnsOne() {
        final Path fake =
                tempDir.resolve("no");
        final Path out =
                tempDir.resolve("o.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        fake.toString(),
                        "--baseline", "HEAD",
                        "--output",
                        out.toString(),
                });
        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains(
                "does not exist");
    }

    @Test
    void outputParentMissingReturnsOne()
            throws Exception {
        final Path proj =
                tempDir.resolve("proj");
        Files.createDirectories(proj);
        final Path out =
                tempDir.resolve("nodir")
                        .resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        proj.toString(),
                        "--baseline", "HEAD",
                        "--output",
                        out.toString(),
                });
        assertThat(code).isEqualTo(1);
        assertThat(stderr()).contains(
                "not writable");
    }

    @Test
    void baselineRefMissingReturnsTwo()
            throws Exception {
        final Path repo =
                tempDir.resolve("repo1");
        initGitRepo(repo);
        final Path out =
                tempDir.resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        repo.toString(),
                        "--baseline",
                        "no-such-ref",
                        "--output",
                        out.toString(),
                });
        assertThat(code).isEqualTo(2);
        assertThat(stderr()).contains(
                "Pipeline failed");
    }

    @Test
    void notGitRepoReturnsTwo()
            throws Exception {
        final Path nogit =
                tempDir.resolve("nogit");
        Files.createDirectories(nogit);
        final Path out =
                tempDir.resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        nogit.toString(),
                        "--baseline", "HEAD",
                        "--output",
                        out.toString(),
                });
        assertThat(code).isEqualTo(2);
        assertThat(stderr()).contains(
                "Pipeline failed");
    }

    // ---- Success cases (E2E) ----

    @Test
    void htmlReportGenerated()
            throws Exception {
        final Path repo =
                tempDir.resolve("html");
        initGitRepoWithTwoCommits(repo);
        final String base = gitOut(repo,
                "rev-parse", "HEAD~1");
        final String tgt = gitOut(repo,
                "rev-parse", "HEAD");
        final Path out =
                tempDir.resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        repo.toString(),
                        "--baseline", base,
                        "--target", tgt,
                        "--output",
                        out.toString(),
                });
        assertThat(code).isZero();
        assertThat(Files.exists(out))
                .isTrue();
        final String content =
                Files.readString(out);
        assertThat(content)
                .contains("<!DOCTYPE html>");
        assertThat(content)
                .contains(
                        "Impact Analysis"
                                + " Report");
    }

    @Test
    void markdownReportGenerated()
            throws Exception {
        final Path repo =
                tempDir.resolve("md");
        initGitRepoWithTwoCommits(repo);
        final String base = gitOut(repo,
                "rev-parse", "HEAD~1");
        final String tgt = gitOut(repo,
                "rev-parse", "HEAD");
        final Path out =
                tempDir.resolve("r.md");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        repo.toString(),
                        "--baseline", base,
                        "--target", tgt,
                        "--output",
                        out.toString(),
                        "--format", "md",
                });
        assertThat(code).isZero();
        assertThat(Files.exists(out))
                .isTrue();
        final String content =
                Files.readString(out);
        assertThat(content)
                .contains(
                        "# Impact Analysis"
                                + " Report");
    }

    // ---- Version upgrade E2E ----

    @Test
    void versionUpgradeDetectedInTargetCommit()
            throws Exception {
        final Path libV1 =
                tempDir.resolve("lib-v1");
        writeLibProject(libV1, "1.0.0",
                "v1");
        installLibToM2(libV1);
        final Path libV2 =
                tempDir.resolve("lib-v2");
        writeLibProject(libV2, "2.0.0",
                "v2_result");
        installLibToM2(libV2);
        final Path repo =
                tempDir.resolve("main-repo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email",
                "t@t.com");
        git(repo, "config", "user.name",
                "t");
        writeMainProject(repo, "1.0.0");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "base");
        final String base = gitOut(repo,
                "rev-parse", "HEAD");
        writeMainProject(repo, "2.0.0");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "tgt");
        final String tgt = gitOut(repo,
                "rev-parse", "HEAD");
        final Path out =
                tempDir.resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        repo.toString(),
                        "--baseline", base,
                        "--target", tgt,
                        "--output",
                        out.toString(),
                });
        assertThat(code).isZero();
        final String content =
                Files.readString(out);
        assertThat(content)
                .contains("VERSION_CHANGED");
        assertThat(content)
                .contains("cia.e2e"
                        + ":test-lib:2.0.0");
        assertThat(content)
                .contains(
                        "METHOD_BODY_CHANGED");
        assertThat(content)
                .contains("cia/e2e/Lib");
        assertThat(content)
                .contains("Impact Paths");
        assertThat(content)
                .contains("cia/e2e/App");
    }

    @Test
    void versionUpgradeDetectedInCurrentWorkspace()
            throws Exception {
        final Path libV1 =
                tempDir.resolve("lib-v1");
        writeLibProject(libV1, "1.0.0",
                "v1");
        installLibToM2(libV1);
        final Path libV2 =
                tempDir.resolve("lib-v2");
        writeLibProject(libV2, "2.0.0",
                "v2_result");
        installLibToM2(libV2);
        final Path repo =
                tempDir.resolve("main-ws");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email",
                "t@t.com");
        git(repo, "config", "user.name",
                "t");
        writeMainProject(repo, "1.0.0");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "base");
        final String base = gitOut(repo,
                "rev-parse", "HEAD");
        writeMainProject(repo, "2.0.0");
        modifyAppSource(repo);
        final Path out =
                tempDir.resolve("r.html");
        final int code = newCli().execute(
                new String[]{
                        "--project",
                        repo.toString(),
                        "--baseline", base,
                        "--output",
                        out.toString(),
                });
        assertThat(code).isZero();
        final String content =
                Files.readString(out);
        assertThat(content)
                .contains("VERSION_CHANGED");
        assertThat(content)
                .contains("cia.e2e"
                        + ":test-lib:2.0.0");
        assertThat(content)
                .contains(
                        "METHOD_BODY_CHANGED");
        assertThat(content)
                .contains("cia/e2e/Lib");
        assertThat(content)
                .contains("Impact Paths");
        assertThat(content)
                .contains("cia/e2e/App");
    }

    // ---- Helpers ----

    /**
     * Creates a new CommandLine for the
     * CLI under test.
     *
     * @return configured CommandLine
     */
    private static CommandLine newCli() {
        return ChangeImpactAnalyzeCli
                .newCommandLine(
                        new ChangeImpactAnalyzeCli());
    }

    /**
     * Returns captured stderr as string.
     *
     * @return stderr content
     */
    private String stderr() {
        return errBuf.toString();
    }

    /**
     * Initializes a git repo with a
     * simple Java 8 Maven project and
     * one initial commit.
     *
     * @param dir repo directory
     * @throws Exception if setup fails
     */
    private void initGitRepo(
            final Path dir)
            throws Exception {
        Files.createDirectories(dir);
        git(dir, "init");
        git(dir, "config", "user.email",
                "t@t.com");
        git(dir, "config", "user.name",
                "t");
        writeSimpleProject(dir, "hi");
        git(dir, "add", ".");
        git(dir, "commit", "-m", "init");
    }

    /**
     * Initializes a git repo with two
     * commits for E2E testing.
     *
     * @param dir repo directory
     * @throws Exception if setup fails
     */
    private void initGitRepoWithTwoCommits(
            final Path dir)
            throws Exception {
        initGitRepo(dir);
        writeSimpleProject(dir, "hello");
        git(dir, "add", ".");
        git(dir, "commit", "-m", "tgt");
    }

    /**
     * Writes a simple Java 8 single
     * module Maven pom.xml and source.
     *
     * @param dir       project directory
     * @param greetBody return value for
     *                  the greet method
     * @throws Exception if write fails
     */
    private void writeSimpleProject(
            final Path dir,
            final String greetBody)
            throws Exception {
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>test"
                        + "</groupId>"
                        + "<artifactId>e2e"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0-SNAPSHOT"
                        + "</version>"
                        + "<properties>"
                        + "<maven.compiler"
                        + ".source>8"
                        + "</maven.compiler"
                        + ".source>"
                        + "<maven.compiler"
                        + ".target>8"
                        + "</maven.compiler"
                        + ".target>"
                        + "</properties>"
                        + "</project>";
        Files.writeString(
                dir.resolve("pom.xml"),
                pom);
        final Path srcDir = dir.resolve(
                "src/main/java/test");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("Hello.java"),
                "package test;\n"
                        + "public class Hello "
                        + "{\n"
                        + "  public String "
                        + "greet() {\n"
                        + "    return \""
                        + greetBody + "\";\n"
                        + "  }\n"
                        + "}\n");
    }

    /**
     * Runs a git command in the given
     * directory.
     *
     * @param dir  working directory
     * @param args git subcommand and args
     * @throws Exception if command fails
     */
    private void git(final Path dir,
                     final String... args)
            throws Exception {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add("git");
        for (final String a : args) {
            cmd.add(a);
        }
        final ProcessBuilder pb =
                new ProcessBuilder(cmd)
                        .directory(
                                dir.toFile())
                        .redirectErrorStream(
                                true);
        final Process p = pb.start();
        p.getInputStream().readAllBytes();
        final int code = p.waitFor();
        if (code != 0) {
            throw new IOException(
                    "git " + args[0]
                            + " failed: "
                            + code);
        }
    }

    /**
     * Runs a git command and returns
     * trimmed stdout.
     *
     * @param dir  working directory
     * @param args git subcommand and args
     * @return trimmed stdout
     * @throws Exception if command fails
     */
    private String gitOut(final Path dir,
                          final String... args)
            throws Exception {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add("git");
        for (final String a : args) {
            cmd.add(a);
        }
        final ProcessBuilder pb =
                new ProcessBuilder(cmd)
                        .directory(
                                dir.toFile())
                        .redirectErrorStream(
                                true);
        final Process p = pb.start();
        final String out = new String(
                p.getInputStream()
                        .readAllBytes());
        final int code = p.waitFor();
        if (code != 0) {
            throw new IOException(
                    "git " + args[0]
                            + " failed: "
                            + code);
        }
        return out.trim();
    }

    /**
     * Writes a library Maven project.
     *
     * @param dir     project directory
     * @param version Maven version
     * @param retVal  doWork return value
     * @throws Exception if write fails
     */
    private void writeLibProject(
            final Path dir,
            final String version,
            final String retVal)
            throws Exception {
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>cia.e2e"
                        + "</groupId>"
                        + "<artifactId>"
                        + "test-lib"
                        + "</artifactId>"
                        + "<version>"
                        + version
                        + "</version>"
                        + "<properties>"
                        + "<maven.compiler"
                        + ".source>8"
                        + "</maven.compiler"
                        + ".source>"
                        + "<maven.compiler"
                        + ".target>8"
                        + "</maven.compiler"
                        + ".target>"
                        + "</properties>"
                        + "</project>";
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("pom.xml"), pom);
        final Path srcDir = dir.resolve(
                "src/main/java/cia/e2e");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("Lib.java"),
                "package cia.e2e;\n"
                        + "public class Lib "
                        + "{\n"
                        + "  public String "
                        + "doWork() {\n"
                        + "    return \""
                        + retVal
                        + "\";\n"
                        + "  }\n"
                        + "}\n");
    }

    /**
     * Installs a Maven project to
     * the local repository.
     *
     * @param dir project directory
     * @throws Exception if install fails
     */
    private void installLibToM2(
            final Path dir)
            throws Exception {
        final ProcessBuilder pb =
                new ProcessBuilder(
                        "mvn", "install",
                        "-B", "-q")
                        .directory(
                                dir.toFile())
                        .redirectErrorStream(
                                true);
        final Process p = pb.start();
        p.getInputStream().readAllBytes();
        final int code = p.waitFor();
        if (code != 0) {
            throw new IOException(
                    "mvn install failed: "
                            + code);
        }
    }

    /**
     * Writes a main Maven project
     * depending on test-lib.
     *
     * @param dir        project dir
     * @param libVersion test-lib version
     * @throws Exception if write fails
     */
    private void writeMainProject(
            final Path dir,
            final String libVersion)
            throws Exception {
        final String pom =
                "<?xml version=\"1.0\" "
                        + "encoding=\"UTF-8\"?>"
                        + "<project xmlns="
                        + "\"http://maven.apache"
                        + ".org/POM/4.0.0\">"
                        + "<modelVersion>"
                        + "4.0.0"
                        + "</modelVersion>"
                        + "<groupId>cia.e2e"
                        + "</groupId>"
                        + "<artifactId>"
                        + "main-app"
                        + "</artifactId>"
                        + "<version>"
                        + "1.0.0"
                        + "</version>"
                        + "<properties>"
                        + "<maven.compiler"
                        + ".source>8"
                        + "</maven.compiler"
                        + ".source>"
                        + "<maven.compiler"
                        + ".target>8"
                        + "</maven.compiler"
                        + ".target>"
                        + "</properties>"
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>cia.e2e"
                        + "</groupId>"
                        + "<artifactId>"
                        + "test-lib"
                        + "</artifactId>"
                        + "<version>"
                        + libVersion
                        + "</version>"
                        + "</dependency>"
                        + "</dependencies>"
                        + "</project>";
        Files.writeString(
                dir.resolve("pom.xml"), pom);
        final Path srcDir = dir.resolve(
                "src/main/java/cia/e2e");
        Files.createDirectories(srcDir);
        Files.writeString(
                srcDir.resolve("App.java"),
                "package cia.e2e;\n"
                        + "public class App "
                        + "{\n"
                        + "  public String "
                        + "run() {\n"
                        + "    return "
                        + "new Lib().doWork();"
                        + "\n"
                        + "  }\n"
                        + "}\n");
    }

    /**
     * Modifies App.java source to
     * dirty the workspace.
     *
     * @param dir project directory
     * @throws Exception if write fails
     */
    private void modifyAppSource(
            final Path dir)
            throws Exception {
        final Path src = dir.resolve(
                "src/main/java/cia/e2e"
                        + "/App.java");
        Files.writeString(src,
                "package cia.e2e;\n"
                        + "public class App "
                        + "{\n"
                        + "  public String "
                        + "run() {\n"
                        + "    String r = "
                        + "new Lib().doWork();"
                        + "\n"
                        + "    return r;\n"
                        + "  }\n"
                        + "}\n");
    }
}
