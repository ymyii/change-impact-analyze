package io.github.dependencyanalysis.util;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Cooperative then forcible subprocess-tree termination. */
public final class ProcessTreeTerminator {

    /** Grace period before forcible termination. */
    private static final Duration GRACE = Duration.ofSeconds(5L);

    /** Private utility constructor. */
    private ProcessTreeTerminator() {
    }

    /**
     * Terminates descendants before their owning process and waits for
     * process reaping. This method never hides the caller's interrupt.
     *
     * @param process root process
     */
    public static void terminate(final Process process) {
        final boolean interrupted = Thread.interrupted();
        final List<ProcessHandle> descendants = process.descendants()
                .sorted(Comparator.comparingLong(ProcessHandle::pid)
                        .reversed())
                .toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            final boolean rootExited = process.waitFor(
                    GRACE.toMillis(), TimeUnit.MILLISECONDS);
            final boolean descendantsExited = await(descendants);
            if (!rootExited || !descendantsExited) {
                descendants.forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(GRACE.toMillis(),
                        TimeUnit.MILLISECONDS);
                await(descendants);
            }
        } catch (InterruptedException exception) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean await(
            final List<ProcessHandle> processes)
            throws InterruptedException {
        final CompletableFuture<?>[] futures = processes.stream()
                .filter(ProcessHandle::isAlive)
                .map(ProcessHandle::onExit)
                .toArray(CompletableFuture[]::new);
        if (futures.length == 0) {
            return true;
        }
        try {
            CompletableFuture.allOf(futures).get(
                    GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException exception) {
            return processes.stream().noneMatch(ProcessHandle::isAlive);
        } catch (TimeoutException exception) {
            return false;
        }
    }
}
