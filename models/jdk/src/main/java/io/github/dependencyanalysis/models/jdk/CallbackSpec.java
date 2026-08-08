package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;

import java.util.Arrays;
import java.util.List;

/**
 * One callback invocation emitted by a declarative summary.
 *
 * @param receiverArgument explicit callback receiver argument
 * @param owner callback interface or class owner
 * @param name callback method name
 * @param descriptor callback JVM descriptor
 * @param dispatch WALA invocation dispatch
 * @param inputs callback input tokens
 */
record CallbackSpec(
        int receiverArgument,
        String owner,
        String name,
        String descriptor,
        Dispatch dispatch,
        List<String> inputs) {

    /** Number of callback specification sections. */
    private static final int SECTION_COUNT = 3;

    /** Number of callback target sections. */
    private static final int TARGET_COUNT = 4;

    /** Dispatch position in a callback target. */
    private static final int DISPATCH_POSITION = 3;

    static List<CallbackSpec> parseAll(final String text) {
        if (text == null || text.isBlank() || "-".equals(text)) {
            return List.of();
        }
        return Arrays.stream(text.split("~", -1))
                .map(CallbackSpec::parse).toList();
    }

    private static CallbackSpec parse(final String text) {
        final String[] sections = text.split("@", -1);
        if (sections.length != SECTION_COUNT) {
            throw new JdkModelException(
                    "Invalid callback specification: " + text);
        }
        final int argument;
        try {
            argument = Integer.parseInt(sections[0]);
        } catch (NumberFormatException exception) {
            throw new JdkModelException(
                    "Invalid callback receiver argument: " + text,
                    exception);
        }
        final String[] target = sections[1].split("#", -1);
        if (target.length != TARGET_COUNT) {
            throw new JdkModelException(
                    "Invalid callback target: " + text);
        }
        final List<String> inputs = "-".equals(sections[2])
                ? List.of()
                : Arrays.asList(sections[2].split(",", -1));
        try {
            return new CallbackSpec(argument, target[0], target[1],
                    target[2], Dispatch.valueOf(
                            target[DISPATCH_POSITION]), inputs);
        } catch (IllegalArgumentException exception) {
            throw new JdkModelException(
                    "Invalid callback dispatch: " + text, exception);
        }
    }
}
