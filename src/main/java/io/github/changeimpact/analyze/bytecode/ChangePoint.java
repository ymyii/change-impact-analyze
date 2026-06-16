package io.github.changeimpact.analyze.bytecode;

import io.github.changeimpact.analyze.dependency.ArtifactCoord;

import java.util.Objects;

// Wiki: wiki/features/bytecode-diff-engine.md - Single bytecode change point
/**
 * Immutable bytecode change point
 * describing a single structural
 * or behavioral difference between
 * old and new class files.
 */
public final class ChangePoint {

    /** Owning artifact coordinate. */
    private final ArtifactCoord artifact;

    /** Kind of change. */
    private final ChangePointKind kind;

    /** Internal class name (owner). */
    private final String owner;

    /** Method or field name. */
    private final String name;

    /** Method or field descriptor. */
    private final String descriptor;

    /** Old body hash (nullable). */
    private final String oldHash;

    /** New body hash (nullable). */
    private final String newHash;

    /**
     * Creates a new change point.
     *
     * @param art  artifact coordinate
     * @param kd   change point kind
     * @param own  internal class name
     * @param nam  method/field name
     * @param desc method/field descriptor
     * @param oh   old body hash or null
     * @param nh   new body hash or null
     */
    public ChangePoint(
            final ArtifactCoord art,
            final ChangePointKind kd,
            final String own,
            final String nam,
            final String desc,
            final String oh,
            final String nh) {
        this.artifact =
                Objects.requireNonNull(
                        art, "artifact");
        this.kind =
                Objects.requireNonNull(
                        kd, "kind");
        this.owner =
                Objects.requireNonNull(
                        own, "owner");
        this.name = nam;
        this.descriptor = desc;
        this.oldHash = oh;
        this.newHash = nh;
    }

    /**
     * Returns the artifact coordinate.
     *
     * @return artifact coordinate
     */
    public ArtifactCoord getArtifact() {
        return artifact;
    }

    /**
     * Returns the change point kind.
     *
     * @return change point kind
     */
    public ChangePointKind getKind() {
        return kind;
    }

    /**
     * Returns the internal class name.
     *
     * @return owner internal name
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the method or field name.
     * Null for class-level changes.
     *
     * @return name or null
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the method or field
     * descriptor. Null for class-level
     * changes.
     *
     * @return descriptor or null
     */
    public String getDescriptor() {
        return descriptor;
    }

    /**
     * Returns the old body hash.
     * Only non-null for
     * METHOD_BODY_CHANGED.
     *
     * @return old hash or null
     */
    public String getOldHash() {
        return oldHash;
    }

    /**
     * Returns the new body hash.
     * Only non-null for
     * METHOD_BODY_CHANGED.
     *
     * @return new hash or null
     */
    public String getNewHash() {
        return newHash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChangePoint)) {
            return false;
        }
        final ChangePoint that =
                (ChangePoint) o;
        return artifact.equals(
                        that.artifact)
                && kind == that.kind
                && owner.equals(that.owner)
                && Objects.equals(
                        name, that.name)
                && Objects.equals(
                        descriptor,
                        that.descriptor)
                && Objects.equals(
                        oldHash, that.oldHash)
                && Objects.equals(
                        newHash, that.newHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                artifact, kind, owner,
                name, descriptor,
                oldHash, newHash);
    }

    @Override
    public String toString() {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("ChangePoint{")
                .append("artifact=")
                .append(artifact)
                .append(", kind=")
                .append(kind)
                .append(", owner=")
                .append(owner)
                .append(", name=")
                .append(name)
                .append(", descriptor=")
                .append(descriptor)
                .append(", oldHash=")
                .append(oldHash)
                .append(", newHash=")
                .append(newHash)
                .append('}');
        return sb.toString();
    }
}
