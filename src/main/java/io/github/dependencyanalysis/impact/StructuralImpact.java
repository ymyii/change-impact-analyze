package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CodeOrigin;

import java.util.Objects;

/** Class metadata reference that cannot be represented as a method path. */
public final class StructuralImpact {

    /** Owning module. */
    private final ModuleId moduleId;

    /** Changed dependency element. */
    private final BoundChangePoint changePoint;

    /** Referencing class. */
    private final String referencingClass;

    /** Referencing class origin. */
    private final CodeOrigin origin;

    /** Stable evidence. */
    private final String evidence;

    /**
     * Creates a structural impact.
     *
     * @param module owning analysis module
     * @param point changed dependency element
     * @param owner referencing class
     * @param value code origin
     * @param detail metadata evidence
     */
    public StructuralImpact(
            final ModuleId module,
            final BoundChangePoint point,
            final String owner,
            final CodeOrigin value,
            final String detail) {
        moduleId = Objects.requireNonNull(module, "moduleId");
        changePoint = Objects.requireNonNull(point, "changePoint");
        referencingClass = Objects.requireNonNull(owner, "owner");
        origin = Objects.requireNonNull(value, "origin");
        evidence = Objects.requireNonNull(detail, "evidence");
    }

    /** @return owning analysis module */
    public ModuleId getModuleId() {
        return moduleId;
    }

    /** @return bound ChangePoint */
    public BoundChangePoint getChangePoint() {
        return changePoint;
    }

    /** @return referencing class */
    public String getReferencingClass() {
        return referencingClass;
    }

    /** @return referencing code origin */
    public CodeOrigin getOrigin() {
        return origin;
    }

    /** @return metadata evidence */
    public String getEvidence() {
        return evidence;
    }
}
