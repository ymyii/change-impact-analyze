package io.github.dependencyanalysis.callgraph.scope;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;

import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.classpath.ClassSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** WALA module view exposing only effective class definitions. */
public final class OwnershipFilteredModule implements Module {

    /** Original classpath module. */
    private final Module delegate;

    /** Physical directory or JAR represented by the module. */
    private final ClassSource source;

    /** Effective class ownership. */
    private final ClassOwnershipIndex ownership;

    /**
     * Creates a filtered WALA module view.
     *
     * @param original underlying module
     * @param sourcePath physical classpath source
     * @param ownershipIndex effective ownership
     */
    public OwnershipFilteredModule(
            final Module original,
            final Path sourcePath,
            final ClassOwnershipIndex ownershipIndex) {
        this(original, ClassSource.path(sourcePath), ownershipIndex);
    }

    /**
     * Creates a filtered WALA module view for a logical source.
     *
     * @param original underlying module
     * @param logicalSource logical classpath source
     * @param ownershipIndex effective ownership
     */
    public OwnershipFilteredModule(
            final Module original,
            final ClassSource logicalSource,
            final ClassOwnershipIndex ownershipIndex) {
        delegate = Objects.requireNonNull(original, "delegate");
        source = Objects.requireNonNull(logicalSource, "logicalSource");
        ownership = Objects.requireNonNull(ownershipIndex, "ownership");
    }

    @Override
    public Iterator<? extends ModuleEntry> getEntries() {
        final List<ModuleEntry> entries = new ArrayList<>();
        final Iterator<? extends ModuleEntry> iterator =
                delegate.getEntries();
        while (iterator.hasNext()) {
            final ModuleEntry entry = iterator.next();
            if (included(entry)) {
                entries.add(entry);
            }
        }
        return entries.iterator();
    }

    private boolean included(final ModuleEntry entry) {
        return !entry.isClassFile()
                || ownership.isEffectiveDefinition(
                entry.getClassName(), source);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
