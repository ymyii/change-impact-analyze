package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.util.ProcessConsoleResult;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;

import io.github.dependencyanalysis.util.ProcessConsoleExecutor;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;

import io.github.dependencyanalysis.util
        .CommandResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Opens current checkout or detached local-ref snapshots. */
public final class GitSnapshotProvider {

    /** Console destination. */
    private final DiagnosticLog diagnostics;

    /** Creates a provider using the default Console destination. */
    public GitSnapshotProvider() {
        this(new DiagnosticLog());
    }

    /**
     * Creates a provider for a command.
     * @param log command Console destination
     */
    public GitSnapshotProvider(
            final DiagnosticLog log) {
        diagnostics = log;
    }


    /**
     * Opens a repository snapshot.
     *
     * @param repository user repository path
     * @param ref local ref, nullable
     * @return owned snapshot
     * @throws IOException on Git or IO failure
     * @throws InterruptedException when interrupted
     */
    public RepositorySnapshot open(
            final Path repository,
            final String ref)
            throws IOException,
            InterruptedException {
        return open(repository, ref, null);
    }

    /**
     * Opens a snapshot in a command-owned workspace directory.
     *
     * @param repository user repository path
     * @param ref local ref, nullable
     * @param workspaceDirectory workspaces/run-id directory
     * @return owned snapshot
     * @throws IOException on Git or IO failure
     * @throws InterruptedException when interrupted
     */
    public RepositorySnapshot open(
            final Path repository,
            final String ref,
            final Path workspaceDirectory)
            throws IOException,
            InterruptedException {
        if (!Files.isDirectory(repository)) {
            throw new IOException(
                    "Analysis path is not a directory: "
                            + repository);
        }
        final Path requestedPath = repository
                .toRealPath().normalize();
        final Path root = Path.of(run(
                requestedPath, "rev-parse",
                "--show-toplevel").trim())
                .toRealPath().normalize();
        if (!requestedPath.startsWith(root)) {
            throw new IOException(
                    "Analysis path is outside Git root: "
                            + requestedPath);
        }
        final Path analysisPath = root
                .relativize(requestedPath);
        if (ref == null || ref.isBlank()) {
            final String commit = run(root,
                    "rev-parse", "HEAD").trim();
            final String branch = optionalRun(root,
                    "symbolic-ref", "--short",
                    "HEAD").trim();
            final boolean dirty = !run(root,
                    "status", "--porcelain",
                    "--untracked-files=all")
                    .isBlank();
            return new RepositorySnapshot(
                    root, root, "current checkout",
                    commit, branch, dirty, () -> { })
                    .withAnalysisPath(analysisPath);
        }
        final String commit = run(root,
                "rev-parse", "--verify",
                ref + "^{commit}").trim();
        final Path worktree;
        if (workspaceDirectory == null) {
            worktree = Files.createTempDirectory(
                    "dependency-analyzer-ref-");
        } else {
            worktree = workspaceDirectory
                    .resolve("worktree");
            Files.createDirectories(
                    workspaceDirectory);
        }
        try {
            run(root, "worktree", "add",
                    "--detach", worktree.toString(),
                    commit);
            final Path mappedAnalysisPath = worktree
                    .resolve(analysisPath).normalize();
            if (!Files.isDirectory(mappedAnalysisPath)) {
                throw new IOException(
                        "Analysis path does not exist at ref "
                                + ref + ": "
                                + analysisPath);
            }
        } catch (IOException exception) {
            cleanup(root, worktree);
            throw exception;
        }
        return new RepositorySnapshot(
                worktree, root, ref, commit,
                "detached", false,
                () -> cleanup(root, worktree))
                .withAnalysisPath(analysisPath);
    }

    private String run(
            final Path directory,
            final String... arguments)
            throws IOException,
            InterruptedException {
        final List<String> command =
                new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        final ProcessBuilder builder = new ProcessBuilder(
                CommandResolver.resolve(command)).directory(directory.toFile());
        final DiagnosticContext context =
                DiagnosticContext.of(
                        "workspace", "git." + arguments[0]);
        final ProcessConsoleResult result =
                arguments[0].equals("worktree")
                ? ProcessConsoleExecutor.execute(
                        builder, diagnostics, context)
                : ProcessConsoleExecutor.executeData(
                        builder, diagnostics, context);
        final int exitCode = result.exitCode();
        if (exitCode != 0) {
            throw new IOException(
                    "Git command failed ("
                            + exitCode + "): "
                            + arguments[0] + "; path=" + directory);
        }
        return result.standardOutput();
    }

    private String optionalRun(
            final Path directory,
            final String... arguments) {
        try {
            return run(directory, arguments);
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private void cleanup(
            final Path root,
            final Path worktree) {
        try {
            run(root, "worktree", "remove",
                    "--force", worktree.toString());
        } catch (IOException exception) {
            try {
                deleteTree(worktree);
            } catch (IOException ignored) {
                // Best-effort cleanup.
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteTree(final Path root)
            throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream =
                     Files.walk(root)) {
            final Path[] paths = stream.sorted(
                    Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
