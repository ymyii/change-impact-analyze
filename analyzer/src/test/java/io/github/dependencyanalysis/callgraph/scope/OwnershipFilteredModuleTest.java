package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.classpath.CodeOrigin;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests winner-only WALA Module exposure. */
class OwnershipFilteredModuleTest {

    /** Temporary classpath roots. */
    @TempDir
    private Path temporary;

    @Test
    void hidesLoserClassButKeepsUniqueClassAndResource() throws Exception {
        final Path winner = temporary.resolve("winner");
        final Path loser = temporary.resolve("loser");
        write(winner, "sample/Duplicate.class", new byte[]{1});
        write(loser, "sample/Duplicate.class", new byte[]{2});
        write(loser, "sample/Unique.class", new byte[]{1});
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        ownership.addDirectory(loser, CodeOrigin.DEPENDENCY);
        final Module delegate = new FakeModule(List.of(
                new FakeEntry("sample/Duplicate.class", true),
                new FakeEntry("sample/Unique.class", true),
                new FakeEntry("module-info.class", true),
                new FakeEntry("META-INF/versions/9/sample/Unique.class",
                        true),
                new FakeEntry("META-INF/services/sample.Service", false)));

        final List<String> entries = entries(new OwnershipFilteredModule(
                delegate, loser, ownership));

        assertThat(entries).containsExactly(
                "sample/Unique.class", "META-INF/services/sample.Service");
    }

    @Test
    void exposesWinnerClass() throws Exception {
        final Path winner = temporary.resolve("winner-source");
        final Path loser = temporary.resolve("loser-source");
        write(winner, "sample/Duplicate.class", new byte[]{1});
        write(loser, "sample/Duplicate.class", new byte[]{2});
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        ownership.addDirectory(loser, CodeOrigin.DEPENDENCY);
        final Module delegate = new FakeModule(List.of(
                new FakeEntry("sample/Duplicate.class", true)));

        assertThat(entries(new OwnershipFilteredModule(
                delegate, winner, ownership)))
                .containsExactly("sample/Duplicate.class");
    }

    private List<String> entries(final Module module) {
        final java.util.ArrayList<String> result =
                new java.util.ArrayList<>();
        module.getEntries().forEachRemaining(entry -> result.add(
                entry.getName()));
        return result;
    }

    private void write(
            final Path root,
            final String relative,
            final byte[] bytes) throws Exception {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** Fixed test Module. */
    private static final class FakeModule implements Module {

        /** Entries. */
        private final List<ModuleEntry> entries;

        private FakeModule(final List<ModuleEntry> values) {
            entries = values;
        }

        @Override
        public Iterator<? extends ModuleEntry> getEntries() {
            return entries.iterator();
        }
    }

    /** Fixed test ModuleEntry. */
    private static final class FakeEntry implements ModuleEntry {

        /** Entry name. */
        private final String name;

        /** Class marker. */
        private final boolean classFile;

        private FakeEntry(final String value, final boolean classEntry) {
            name = value;
            classFile = classEntry;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isClassFile() {
            return classFile;
        }

        @Override
        public boolean isSourceFile() {
            return false;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public boolean isModuleFile() {
            return false;
        }

        @Override
        public Module asModule() {
            return null;
        }

        @Override
        public String getClassName() {
            return classFile
                    ? name.substring(0, name.length() - ".class".length())
                    : name;
        }

        @Override
        public Module getContainer() {
            return null;
        }
    }
}
