package io.github.dependencyanalysis.workspace;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for
 * {@link WorkspaceManager}.
 */
class WorkspaceManagerIT {

    @TempDir
    private Path tempDir;

    /** Repo directory inside tempDir. */
    private Path repoDir;

    /** First commit hash. */
    private String firstCommit;

    /** Second commit hash. */
    private String secondCommit;

    /** Diagnostics collector. */
    private DiagnosticLog diag;

    /** Captured Console output. */
    private ByteArrayOutputStream console;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tempDir.resolve("repo");
        Files.createDirectories(repoDir);
        console = new ByteArrayOutputStream();
        diag = new DiagnosticLog(new PrintStream(
                console), LogVerbosity.INFO);
        git("init");
        git("config", "user.email",
                "test@test.com");
        git("config", "user.name", "test");
        Files.writeString(
                repoDir.resolve("file.txt"),
                "v1");
        git("add", ".");
        git("commit", "-m", "first");
        firstCommit = gitOutput(
                "rev-parse", "HEAD");
        Files.writeString(
                repoDir.resolve("file.txt"),
                "v2");
        git("add", ".");
        git("commit", "-m", "second");
        secondCommit = gitOutput(
                "rev-parse", "HEAD");
    }

    @Test
    void baselineAndCurrentMode()
            throws Exception {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            final WorkspaceResult result =
                    mgr.prepare(
                            firstCommit, null);
            assertThat(result).isNotNull();
            final WorkspaceSideInfo base =
                    result.getBaseline();
            assertThat(base.getSide())
                    .isEqualTo(
                            WorkspaceSide
                                    .BASELINE);
            assertThat(base.getCommit())
                    .isEqualTo(firstCommit);
            assertThat(base.isWhetherTemporary())
                    .isTrue();
            assertThat(Files.exists(
                    base.getPath())).isTrue();
            final WorkspaceSideInfo target =
                    result.getTarget();
            assertThat(target.getSide())
                    .isEqualTo(
                            WorkspaceSide.TARGET);
            assertThat(target.getCommit())
                    .isEqualTo(secondCommit);
            assertThat(target.isWhetherTemporary())
                    .isFalse();
            assertThat(target.getPath())
                    .isEqualTo(repoDir);
        }
    }

    @Test
    void baselineAndTargetCommitMode()
            throws Exception {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            final WorkspaceResult result =
                    mgr.prepare(
                            firstCommit,
                            secondCommit);
            final WorkspaceSideInfo target =
                    result.getTarget();
            assertThat(target.getSide())
                    .isEqualTo(
                            WorkspaceSide.TARGET);
            assertThat(target.getCommit())
                    .isEqualTo(secondCommit);
            assertThat(target.isWhetherTemporary())
                    .isTrue();
            assertThat(target.getPath())
                    .isNotEqualTo(repoDir);
            assertThat(Files.exists(
                    target.getPath())).isTrue();
        }
    }

    @Test
    void annotatedTagsPeelToCommits() throws Exception {
        git("tag", "-a", "release-first", firstCommit,
                "-m", "release first");
        try (WorkspaceManager manager = new WorkspaceManager(
                repoDir, diag)) {
            final WorkspaceResult result = manager.prepare(
                    "release-first", "HEAD");

            assertThat(result.getBaseline().getCommit())
                    .isEqualTo(firstCommit);
            assertThat(result.getTarget().getCommit())
                    .isEqualTo(secondCommit);
        }
    }

    @Test
    void commandOwnedWorktreesStayInsideRunDirectory()
            throws Exception {
        final Path runRoot = tempDir.resolve(
                "config/impact/workspaces/run-id");
        Files.createDirectories(runRoot);
        try (WorkspaceManager manager = new WorkspaceManager(
                repoDir, diag, runRoot)) {
            final WorkspaceResult result = manager.prepare(
                    firstCommit, secondCommit);
            assertThat(result.getBaseline().getPath())
                    .startsWith(runRoot);
            assertThat(result.getTarget().getPath())
                    .startsWith(runRoot);
        }
        assertThat(runRoot.resolve("baseline")).doesNotExist();
        assertThat(runRoot.resolve("target")).doesNotExist();
    }

    @Test
    void currentWorkspaceNotCheckedOut()
            throws Exception {
        final String headBefore =
                gitOutput("rev-parse",
                        "HEAD");
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            mgr.prepare(firstCommit, null);
            final String headAfter =
                    gitOutput("rev-parse",
                            "HEAD");
            assertThat(headAfter)
                    .isEqualTo(headBefore);
        }
    }

    @Test
    void currentWorkspaceNotStashed()
            throws Exception {
        Files.writeString(
                repoDir.resolve("dirty.txt"),
                "dirty");
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            final WorkspaceResult result = mgr.prepare(
                    firstCommit, null);
            assertThat(result.getTarget().isDirty()).isTrue();
            assertThat(Files.exists(
                    repoDir.resolve(
                            "dirty.txt")))
                    .isTrue();
        }
    }

    @Test
    void nonExistentCommitFailsDiagnosable() {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            assertThatThrownBy(() ->
                    mgr.prepare(
                            "nonexistent-ref",
                            null))
                    .isInstanceOf(
                            WorkspacePrepareException
                                    .class)
                    .satisfies(ex -> {
                        final WorkspacePrepareException
                                wpe =
                                (WorkspacePrepareException)
                                        ex;
                        assertThat(wpe.getSide())
                                .isEqualTo(
                                        WorkspaceSide
                                                .BASELINE);
                        assertThat(
                                wpe.getCommit())
                                .isEqualTo(
                                        "nonexistent-ref");
                        assertThat(
                                wpe.getGitExitCode())
                                .isNotZero();
                        assertThat(
                                wpe.getReason())
                                .isNotBlank();
                    });
        }
    }

    @Test
    void nonExistentTargetCommitFails() {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            assertThatThrownBy(() ->
                    mgr.prepare(
                            firstCommit,
                            "no-such-ref"))
                    .isInstanceOf(
                            WorkspacePrepareException
                                    .class)
                    .satisfies(ex -> {
                        final WorkspacePrepareException
                                wpe =
                                (WorkspacePrepareException)
                                        ex;
                        assertThat(wpe.getSide())
                                .isEqualTo(
                                        WorkspaceSide
                                                .TARGET);
                    });
        }
    }

    @Test
    void worktreesCleanedUpOnClose()
            throws Exception {
        final Path wtPath;
        final WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag);
        try {
            final WorkspaceResult result =
                    mgr.prepare(
                            firstCommit,
                            secondCommit);
            wtPath = result.getBaseline()
                    .getPath();
            assertThat(Files.exists(wtPath))
                    .isTrue();
        } finally {
            mgr.close();
        }
        assertThat(Files.exists(wtPath))
                .isFalse();
    }

    @Test
    void worktreesCleanedUpOnFailure()
            throws Exception {
        final WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag);
        Path baseWtPath = null;
        try {
            try {
                mgr.prepare(
                        firstCommit,
                        "bad-target-ref");
            } catch (WorkspacePrepareException e) {
                // expected
            }
        } finally {
            mgr.close();
        }
        assertThat(console.toString()).contains("[ERROR][workspace]");
    }

    @Test
    void diagnosticEventsEmitted()
            throws Exception {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            mgr.prepare(firstCommit, null);
        }
        assertThat(console.toString())
                .contains("[INFO][workspace]")
                .contains(" started")
                .contains(" completed; elapsedMs=")
                .contains("Baseline commit");
    }

    @Test
    void closeIsIdempotent()
            throws Exception {
        final WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag);
        mgr.prepare(firstCommit, null);
        mgr.close();
        mgr.close();
    }

    @Test
    void resultContainsSidePathCommitTemp()
            throws Exception {
        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        repoDir, diag)) {
            final WorkspaceResult result =
                    mgr.prepare(
                            firstCommit,
                            secondCommit);
            for (final WorkspaceSideInfo info :
                    new WorkspaceSideInfo[] {
                            result.getBaseline(),
                            result.getTarget()}) {
                assertThat(info.getSide())
                        .isNotNull();
                assertThat(info.getPath())
                        .isNotNull();
                assertThat(info.getCommit())
                        .isNotBlank();
            }
            assertThat(result.getBaseline()
                    .isWhetherTemporary())
                    .isTrue();
            assertThat(result.getTarget()
                    .isWhetherTemporary())
                    .isTrue();
        }
    }

    @Test
    void subdirectoryProjectAlignsPaths()
            throws Exception {
        final Path subDir =
                repoDir.resolve("sub")
                        .resolve("module");
        Files.createDirectories(subDir);
        Files.writeString(
                subDir.resolve("pom.xml"),
                "<project/>");
        git("add", ".");
        git("commit", "-m", "add sub");
        final String subCommit =
                gitOutput("rev-parse", "HEAD");

        Files.writeString(
                repoDir.resolve("file.txt"),
                "v3");
        git("add", ".");
        git("commit", "-m", "third");
        final String thirdCommit =
                gitOutput("rev-parse", "HEAD");

        try (WorkspaceManager mgr =
                new WorkspaceManager(
                        subDir, diag)) {
            final WorkspaceResult result =
                    mgr.prepare(
                            subCommit,
                            thirdCommit);
            final WorkspaceSideInfo base =
                    result.getBaseline();
            assertThat(base.getPath())
                    .endsWith(Path.of("sub",
                            "module"));
            assertThat(Files.exists(
                    base.getPath().resolve(
                            "pom.xml")))
                    .isTrue();
            final WorkspaceSideInfo target =
                    result.getTarget();
            assertThat(target.getPath())
                    .endsWith(Path.of("sub",
                            "module"));
            assertThat(target.isWhetherTemporary())
                    .isTrue();
            assertThat(Files.exists(
                    target.getPath().resolve(
                            "pom.xml")))
                    .isTrue();
        }
    }

    @Test
    void nonGitDirectoryThrowsException()
            throws Exception {
        final Path nonGitDir =
                tempDir.resolve("non-git");
        Files.createDirectories(nonGitDir);
        assertThatThrownBy(() ->
                new WorkspaceManager(
                        nonGitDir, diag))
                .isInstanceOf(
                        IllegalStateException
                                .class)
                .hasMessageContaining(
                        "not inside a git"
                                + " repository");
    }

    private void git(final String... args)
            throws Exception {
        gitCmd(repoDir, args);
    }

    private void gitCmd(final Path dir,
            final String... args)
            throws Exception {
        final ProcessBuilder pb =
                new ProcessBuilder()
                        .command(prependGit(args))
                        .directory(dir.toFile())
                        .redirectErrorStream(
                                true);
        final Process p = pb.start();
        p.getInputStream().readAllBytes();
        final int code = p.waitFor();
        if (code != 0) {
            throw new IOException(
                    "git " + args[0]
                            + " failed: " + code);
        }
    }

    private String gitOutput(
            final String... args)
            throws Exception {
        return gitCmdOutput(repoDir, args);
    }

    private String gitCmdOutput(final Path dir,
            final String... args)
            throws Exception {
        final ProcessBuilder pb =
                new ProcessBuilder()
                        .command(prependGit(args))
                        .directory(dir.toFile())
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
                            + " failed: " + code);
        }
        return out.trim();
    }

    private List<String> prependGit(
            final String... args) {
        final List<String> cmd =
                new java.util.ArrayList<>();
        cmd.add("git");
        for (final String a : args) {
            cmd.add(a);
        }
        return cmd;
    }
}
