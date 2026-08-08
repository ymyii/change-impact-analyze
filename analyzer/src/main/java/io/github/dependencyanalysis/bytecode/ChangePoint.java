package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.util.Objects;
import java.util.Optional;

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

    /** Old method or field descriptor. */
    private final String oldDescriptor;

    /** New method or field descriptor. */
    private final String newDescriptor;

    /** Old body hash (nullable). */
    private final String oldHash;

    /** New body hash (nullable). */
    private final String newHash;

    /** Strict access narrowing, present only for access kinds. */
    private final AccessTransition accessTransition;

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
        this(art, kd, own, nam,
                new ChangeDetails(new MemberDescriptors(desc, desc),
                        oh, nh, null));
    }

    /**
     * Creates a change point with explicit
     * old and new member descriptors.
     *
     * @param art artifact coordinate
     * @param kd change point kind
     * @param own internal class name
     * @param nam member name or null
     * @param details descriptors, body hashes and access transition
     */
    private ChangePoint(
            final ArtifactCoord art,
            final ChangePointKind kd,
            final String own,
            final String nam,
            final ChangeDetails details) {
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
        final MemberDescriptors pair =
                Objects.requireNonNull(
                        details.descriptors(),
                        "descriptors");
        this.oldDescriptor =
                pair.getOldDescriptor();
        this.newDescriptor =
                pair.getNewDescriptor();
        this.oldHash = details.oldHash();
        this.newHash = details.newHash();
        this.accessTransition = details.accessTransition();
        if (kind.isAccessNarrowing() != (accessTransition != null)) {
            throw new IllegalArgumentException(
                    "Access narrowing kind and transition must agree");
        }
    }

    /**
     * Creates a change point with explicit old and new descriptors.
     *
     * @param art artifact coordinate
     * @param kd change point kind
     * @param own internal class name
     * @param nam member name or null
     * @param descriptors old/new descriptors
     * @param oh old body hash or null
     * @param nh new body hash or null
     * @return change point
     */
    public static ChangePoint withDescriptors(
            final ArtifactCoord art,
            final ChangePointKind kd,
            final String own,
            final String nam,
            final MemberDescriptors descriptors,
            final String oh,
            final String nh) {
        return new ChangePoint(art, kd, own, nam,
                new ChangeDetails(descriptors, oh, nh, null));
    }

    /**
     * Creates a validated strict JVM access narrowing ChangePoint.
     *
     * @param art artifact coordinate
     * @param kd access narrowing kind
     * @param own internal class name
     * @param nam member name, or null for class access
     * @param descriptor exact member descriptor, or null for class access
     * @param transition strict old/new access
     * @return access narrowing ChangePoint
     */
    public static ChangePoint accessNarrowed(
            final ArtifactCoord art,
            final ChangePointKind kd,
            final String own,
            final String nam,
            final String descriptor,
            final AccessTransition transition) {
        if (!Objects.requireNonNull(kd, "kd").isAccessNarrowing()) {
            throw new IllegalArgumentException(
                    "Expected access narrowing ChangePoint kind");
        }
        return new ChangePoint(art, kd, own, nam,
                new ChangeDetails(
                        new MemberDescriptors(descriptor, descriptor),
                        null, null, Objects.requireNonNull(
                                transition, "transition")));
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
        return newDescriptor == null
                ? oldDescriptor
                : newDescriptor;
    }

    /**
     * Returns the old member descriptor.
     *
     * @return old descriptor or null
     */
    public String getOldDescriptor() {
        return oldDescriptor;
    }

    /**
     * Returns the new member descriptor.
     *
     * @return new descriptor or null
     */
    public String getNewDescriptor() {
        return newDescriptor;
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

    /** @return strict access transition for access narrowing kinds */
    public Optional<AccessTransition> getAccessTransition() {
        return Optional.ofNullable(accessTransition);
    }

    /**
     * Canonical optional ChangePoint details.
     *
     * @param descriptors old/new member descriptors
     * @param oldHash old method body hash
     * @param newHash new method body hash
     * @param accessTransition strict access narrowing
     */
    private record ChangeDetails(
            MemberDescriptors descriptors,
            String oldHash,
            String newHash,
            AccessTransition accessTransition) {

        ChangeDetails {
            Objects.requireNonNull(descriptors, "descriptors");
        }
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
                        oldDescriptor,
                        that.oldDescriptor)
                && Objects.equals(
                        newDescriptor,
                        that.newDescriptor)
                && Objects.equals(
                        oldHash, that.oldHash)
                && Objects.equals(
                        newHash, that.newHash)
                && Objects.equals(accessTransition,
                        that.accessTransition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                artifact, kind, owner,
                name, oldDescriptor,
                newDescriptor,
                oldHash, newHash, accessTransition);
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
                .append(", oldDescriptor=")
                .append(oldDescriptor)
                .append(", newDescriptor=")
                .append(newDescriptor)
                .append(", oldHash=")
                .append(oldHash)
                .append(", newHash=")
                .append(newHash)
                .append(", accessTransition=")
                .append(accessTransition)
                .append('}');
        return sb.toString();
    }
}
