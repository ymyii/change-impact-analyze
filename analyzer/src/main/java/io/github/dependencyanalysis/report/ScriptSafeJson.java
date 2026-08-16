package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;

/** Shared JSON factory safe for HTML scripts and JavaScript shards. */
final class ScriptSafeJson {

    /** Unicode line separator. */
    private static final int LINE_SEPARATOR = 0x2028;

    /** Unicode paragraph separator. */
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    /** Shared thread-safe factory. */
    private static final JsonFactory FACTORY = create();

    /** Utility class. */
    private ScriptSafeJson() {
    }

    /** @return shared script-safe factory */
    static JsonFactory factory() {
        return FACTORY;
    }

    private static JsonFactory create() {
        final JsonFactory result = new JsonFactory();
        result.setCharacterEscapes(new SafeEscapes());
        return result;
    }

    /** Prevents JSON values from becoming executable HTML or script text. */
    private static final class SafeEscapes extends CharacterEscapes {

        /** Standard JSON escapes plus HTML-sensitive ASCII characters. */
        private final int[] asciiEscapes;

        SafeEscapes() {
            asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
            asciiEscapes['<'] = CharacterEscapes.ESCAPE_CUSTOM;
            asciiEscapes['>'] = CharacterEscapes.ESCAPE_CUSTOM;
            asciiEscapes['&'] = CharacterEscapes.ESCAPE_CUSTOM;
        }

        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override
        public SerializableString getEscapeSequence(final int character) {
            return switch (character) {
                case '<' -> new SerializedString("\\u003c");
                case '>' -> new SerializedString("\\u003e");
                case '&' -> new SerializedString("\\u0026");
                case LINE_SEPARATOR -> new SerializedString("\\u2028");
                case PARAGRAPH_SEPARATOR ->
                        new SerializedString("\\u2029");
                default -> null;
            };
        }
    }
}
