package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.classpath.CodeOrigin;

import java.util.Objects;

/** Structured class-file metadata reference to a changed class. */
public final class StructuralReference {

    /** Referencing binary class name. */
    private final String referencingClass;

    /** Referencing class origin. */
    private final CodeOrigin origin;

    /** Java metadata relation. */
    private final StructuralReferenceKind kind;

    /** Referencing member identity, empty for class-level metadata. */
    private final String referencingMember;

    /** Changed dependency binary class name. */
    private final String changedClass;

    /** Stable raw metadata evidence. */
    private final String evidence;

    /**
     * Creates a structural reference.
     *
     * @param owner referencing class
     * @param codeOrigin referencing origin
     * @param relation metadata relation
     * @param member referencing member, empty for class-level metadata
     * @param target changed dependency class
     * @param detail stable raw evidence
     */
    public StructuralReference(
            final String owner,
            final CodeOrigin codeOrigin,
            final StructuralReferenceKind relation,
            final String member,
            final String target,
            final String detail) {
        referencingClass = Objects.requireNonNull(owner, "owner");
        origin = Objects.requireNonNull(codeOrigin, "origin");
        kind = Objects.requireNonNull(relation, "relation");
        referencingMember = Objects.requireNonNull(member, "member");
        changedClass = Objects.requireNonNull(target, "target");
        evidence = Objects.requireNonNull(detail, "detail");
    }

    /** @return referencing binary class name */
    public String getReferencingClass() {
        return referencingClass;
    }

    /** @return referencing code origin */
    public CodeOrigin getOrigin() {
        return origin;
    }

    /** @return metadata relation */
    public StructuralReferenceKind getKind() {
        return kind;
    }

    /** @return member identity, or empty for class-level metadata */
    public String getReferencingMember() {
        return referencingMember;
    }

    /** @return changed dependency binary class name */
    public String getChangedClass() {
        return changedClass;
    }

    /** @return stable raw evidence */
    public String getEvidence() {
        return evidence;
    }

    /** @return stable reference identity */
    public String stableKey() {
        return referencingClass + "|" + origin + "|" + kind + "|"
                + referencingMember + "|" + changedClass + "|" + evidence;
    }
}
