package io.github.dependencyanalysis.preflight;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Prepared context shared by preflight and pipeline. */
public final class PreflightContext
        implements AutoCloseable {

    /** Prepared values. */
    private final Map<String, Object> values =
            new LinkedHashMap<>();

    /** Owned resources in close order. */
    private final java.util.List<AutoCloseable>
            resources = new java.util.ArrayList<>();

    /**
     * Stores a prepared value.
     *
     * @param key stable key
     * @param value value
     * @param <T> value type
     * @return stored value
     */
    public <T> T put(
            final String key,
            final T value) {
        values.put(Objects.requireNonNull(
                key, "key"), value);
        return value;
    }

    /**
     * Reads a prepared value.
     *
     * @param key stable key
     * @param type expected type
     * @param <T> value type
     * @return value, or null
     */
    public <T> T get(
            final String key,
            final Class<T> type) {
        final Object value = values.get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    /**
     * Registers an owned resource.
     *
     * @param resource resource
     * @param <T> resource type
     * @return same resource
     */
    public <T extends AutoCloseable> T own(
            final T resource) {
        resources.add(Objects.requireNonNull(
                resource, "resource"));
        return resource;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (int index = resources.size() - 1;
             index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = new RuntimeException(
                            "Prepared resource cleanup failed",
                            exception);
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        resources.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
