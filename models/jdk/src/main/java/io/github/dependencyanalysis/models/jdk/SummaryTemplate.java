package io.github.dependencyanalysis.models.jdk;

/** Supported declarative Synthetic IR templates. */
enum SummaryTemplate {

    /** Return a default value without other effects. */
    NO_OP,

    /** Return the receiver. */
    RETURN_THIS,

    /** Return one explicit argument. */
    RETURN_ARG,

    /** Allocate and return an instance of the declared return type. */
    RETURN_NEW,

    /** Store one explicit argument in a model state slot. */
    STORE,

    /** Store one explicit argument and return the receiver. */
    STORE_RETURN_THIS,

    /** Store one explicit argument and allocate the return value. */
    STORE_RETURN_NEW,

    /** Store a map key and value. */
    MAP_STORE,

    /** Store a map key and value, then allocate the map return value. */
    MAP_STORE_RETURN_NEW,

    /** Load and return one model state slot. */
    LOAD,

    /** Copy one slot to another, then allocate the return value. */
    COPY_NEW,

    /** Invoke callbacks and return a default value. */
    CALLBACK_VOID,

    /** Invoke callbacks and return the receiver. */
    CALLBACK_THIS,

    /** Invoke callbacks and return the final callback result. */
    CALLBACK_RESULT,

    /** Invoke callbacks and allocate the declared return type. */
    CALLBACK_NEW,

    /** Model Files.walkFileTree callbacks. */
    FILE_VISITOR,

    /** Seed a resource stream and allocate the stream return value. */
    RESOURCE_STREAM,

    /** Model ObjectOutputStream.writeObject callbacks. */
    SERIALIZE_WRITE,

    /** Model ObjectInputStream.readObject callbacks and return types. */
    SERIALIZE_READ,

    /** Model registered ObjectInputValidation callbacks. */
    SERIALIZE_VALIDATE
}
