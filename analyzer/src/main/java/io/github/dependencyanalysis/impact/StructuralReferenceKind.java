package io.github.dependencyanalysis.impact;

/** Java metadata relation that references a changed dependency class. */
public enum StructuralReferenceKind {

    /** Declared superclass. */
    SUPERCLASS("superclass"),

    /** Implemented or extended interface. */
    INTERFACE("interface"),

    /** Annotation type or annotation value. */
    ANNOTATION("annotation"),

    /** Field type or generic signature. */
    FIELD_TYPE("field type"),

    /** Method parameter type. */
    METHOD_PARAMETER("parameter type"),

    /** Method return type. */
    METHOD_RETURN("return type"),

    /** Declared exception type. */
    THROWS("throws declaration"),

    /** Generic class or method signature. */
    SIGNATURE("generic signature"),

    /** Other class-file metadata relation. */
    METADATA("class metadata");

    /** Plain-language label. */
    private final String label;

    StructuralReferenceKind(final String displayLabel) {
        label = displayLabel;
    }

    /** @return plain-language relation label */
    public String getLabel() {
        return label;
    }
}
