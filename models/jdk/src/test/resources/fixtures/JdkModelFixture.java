package fixture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectInputValidation;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public final class JdkModelFixture {

    private static Object sink;

    private JdkModelFixture() {
    }

    public static void main(String[] args) throws Exception {
        collectionAndStream();
        timeAndConcurrency();
        resources();
        serialization();
    }

    private static void collectionAndStream() {
        BusinessValue value = new BusinessValue();
        List<Object> values = new ArrayList<Object>();
        values.add(value);
        ((BusinessValue) values.get(0)).downstream();

        Map<String, Object> map = new HashMap<String, Object>();
        Object computed = map.computeIfAbsent("key", new Mapper());
        ((BusinessValue) computed).downstream();

        Stream<Object> stream = Stream.of((Object) value);
        stream.map(new Mapper()).filter(new Filter()).forEach(new Sink());
        Optional.of((Object) value).map(new Mapper()).ifPresent(new Sink());
        sink = new StringBuilder().append(value).toString();
    }

    private static void timeAndConcurrency() {
        LocalDate date = LocalDate.now();
        sink = date.query(new DateQuery());
        sink = date.with(new Adjuster());

        ThreadLocal<Object> local = new ThreadLocal<Object>();
        local.set(new BusinessValue());
        ((BusinessValue) local.get()).downstream();

        Executor executor = new DirectExecutor();
        executor.execute(new Task());
        CompletableFuture.supplyAsync(new Supply())
                .thenApply(new Mapper()).thenAccept(new Sink());

        Lock lock = new ReentrantLock();
        lock.lock();
        lock.unlock();
    }

    private static void resources() throws Exception {
        Path path = Paths.get("fixture", new String[0]);
        Files.lines(path).forEach(new Sink());
        Files.walkFileTree(path, new Visitor());

        byte[] bytes = ByteBuffer.wrap(new byte[1]).array();
        sink = Charset.forName("UTF-8").encode("value");
        InputStream input = new URL("https://example.invalid")
                .openConnection().getInputStream();
        sink = new GZIPInputStream(input);
        sink = bytes;
    }

    private static void serialization() throws Exception {
        ObjectOutputStream output = new OutputHooks();
        output.writeObject(new SerialValue());
        output.writeObject(new ExternalValue());

        ObjectInputStream input = new InputHooks();
        sink = input.readObject();
        input.registerValidation(new Validation(), 1);
    }

    public static final class BusinessValue {
        public void downstream() {
            sink = this;
        }

        @Override
        public String toString() {
            downstream();
            return "business";
        }
    }

    public static final class Mapper implements Function<Object, Object> {
        @Override
        public Object apply(Object value) {
            sink = value;
            return new BusinessValue();
        }
    }

    public static final class Filter implements Predicate<Object> {
        @Override
        public boolean test(Object value) {
            sink = value;
            return true;
        }
    }

    public static final class Sink implements Consumer<Object> {
        @Override
        public void accept(Object value) {
            sink = value;
        }
    }

    public static final class DateQuery implements TemporalQuery<Object> {
        @Override
        public Object queryFrom(TemporalAccessor temporal) {
            sink = temporal;
            return new BusinessValue();
        }
    }

    public static final class Adjuster implements TemporalAdjuster {
        @Override
        public Temporal adjustInto(Temporal temporal) {
            sink = temporal;
            return temporal;
        }
    }

    public static final class DirectExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    public static final class Task implements Runnable {
        @Override
        public void run() {
            new BusinessValue().downstream();
        }
    }

    public static final class Supply implements Supplier<Object> {
        @Override
        public Object get() {
            return new BusinessValue();
        }
    }

    public static final class Visitor implements FileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(
                Path dir, BasicFileAttributes attrs) {
            sink = dir;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(
                Path file, BasicFileAttributes attrs) {
            sink = file;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) {
            sink = error;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(
                Path dir, IOException error) {
            sink = error;
            return FileVisitResult.CONTINUE;
        }
    }

    public static class NonSerializableBase {
        public NonSerializableBase() {
            sink = this;
        }
    }

    public static final class SerialValue extends NonSerializableBase
            implements Serializable {
        private static final long serialVersionUID = 1L;

        private void writeObject(ObjectOutputStream output) throws IOException {
            sink = output;
        }

        private void readObject(ObjectInputStream input)
                throws IOException, ClassNotFoundException {
            sink = input;
        }

        private void readObjectNoData() {
            sink = this;
        }

        private Object writeReplace() {
            return this;
        }

        private Object readResolve() {
            return this;
        }
    }

    public static final class ExternalValue implements Externalizable {
        public ExternalValue() {
        }

        @Override
        public void writeExternal(ObjectOutput output) throws IOException {
            sink = output;
        }

        @Override
        public void readExternal(ObjectInput input)
                throws IOException, ClassNotFoundException {
            sink = input;
        }
    }

    public static final class Validation implements ObjectInputValidation {
        @Override
        public void validateObject() {
            sink = this;
        }
    }

    public static final class InputHooks extends ObjectInputStream {
        InputHooks() throws IOException {
            super();
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object object) {
            sink = object;
            return object;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            sink = descriptor;
            return Object.class;
        }

        @Override
        protected ObjectStreamClass readClassDescriptor()
                throws IOException, ClassNotFoundException {
            return ObjectStreamClass.lookup(SerialValue.class);
        }
    }

    public static final class OutputHooks extends ObjectOutputStream {
        OutputHooks() throws IOException {
            super();
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object object) {
            sink = object;
            return object;
        }

        @Override
        protected void annotateClass(Class<?> type) {
            sink = type;
        }

        @Override
        protected void writeClassDescriptor(ObjectStreamClass descriptor) {
            sink = descriptor;
        }
    }
}

final class ReachabilityFixture {
    private ReachabilityFixture() {
    }

    public static void main(String[] args) {
        List<Object> values = new ArrayList<Object>();
        JdkModelFixture.BusinessValue value =
                new JdkModelFixture.BusinessValue();
        values.add(value);
        ((JdkModelFixture.BusinessValue) values.get(0)).downstream();
        new StringBuilder().append(value).toString();
    }
}
