package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;

import java.util.Objects;

/** Local verifier-level receiver abstraction for protected access. */
public final class ReceiverType {

    /** Receiver abstraction kind. */
    public enum Kind {
        /** Static/class reference. */
        NOT_APPLICABLE,
        /** Current this value. */
        THIS,
        /** Legal invokespecial super receiver. */
        SUPER,
        /** Exact verifier type. */
        POINT,
        /** Verifier subtype cone. */
        CONE,
        /** No useful local verifier type. */
        UNKNOWN
    }

    /** Abstraction kind. */
    private final Kind kind;

    /** Point/cone type. */
    private final IClass type;

    private ReceiverType(final Kind value, final IClass receiverType) {
        kind = Objects.requireNonNull(value, "kind");
        type = receiverType;
        if ((kind == Kind.POINT || kind == Kind.CONE) != (type != null)) {
            throw new IllegalArgumentException(
                    "Receiver kind and verifier type must agree");
        }
    }

    /** @return receiver without an instance value */
    public static ReceiverType notApplicable() {
        return new ReceiverType(Kind.NOT_APPLICABLE, null);
    }

    /** @return current this receiver */
    public static ReceiverType thisReceiver() {
        return new ReceiverType(Kind.THIS, null);
    }

    /** @return invokespecial super receiver */
    public static ReceiverType superReceiver() {
        return new ReceiverType(Kind.SUPER, null);
    }

    /**
     * @param type exact verifier type
     * @return exact receiver
     */
    public static ReceiverType point(final IClass type) {
        return new ReceiverType(Kind.POINT,
                Objects.requireNonNull(type, "type"));
    }

    /**
     * @param type cone root
     * @return receiver cone
     */
    public static ReceiverType cone(final IClass type) {
        return new ReceiverType(Kind.CONE,
                Objects.requireNonNull(type, "type"));
    }

    /** @return unknown receiver */
    public static ReceiverType unknown() {
        return new ReceiverType(Kind.UNKNOWN, null);
    }

    /** @return abstraction kind */
    public Kind kind() {
        return kind;
    }

    /** @return point/cone verifier type, or null */
    public IClass type() {
        return type;
    }

    /** @return stable verifier type identity */
    public String stableKey() {
        return kind + (type == null ? "" : ":" + type.getReference());
    }
}
