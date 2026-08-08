package io.github.dependencyanalysis.models.jdk;

/** Conservative reference-flow families shared by modeled JDK methods. */
enum StateSlot {

    /** Collection element. */
    COLLECTION_ELEMENT,

    /** Map key. */
    MAP_KEY,

    /** Map value. */
    MAP_VALUE,

    /** Stream element. */
    STREAM_ELEMENT,

    /** Optional value. */
    OPTIONAL_VALUE,

    /** Future result. */
    FUTURE_RESULT,

    /** Thread-local value. */
    THREAD_LOCAL_VALUE,

    /** Atomic reference value. */
    ATOMIC_VALUE,

    /** Byte-array or byte-buffer value. */
    BYTE_BUFFER,

    /** Character-array or text-buffer value. */
    CHAR_BUFFER,

    /** Resource object. */
    RESOURCE,

    /** Serialization object. */
    SERIALIZED_OBJECT
}
