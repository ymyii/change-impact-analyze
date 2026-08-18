package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.ServiceProviderRegistration;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.impact.StructuralReference;

/** Canonical JVM-exact identities rendered by the offline report. */
final class ReportSignatures {

    private ReportSignatures() {
    }

    /**
     * @param value method identity
     * @return dotted owner, method name and JVM descriptor
     */
    static String method(final MethodId value) {
        return owner(value.owner()) + "#" + value.name()
                + value.descriptor();
    }

    /**
     * @param value changed member
     * @return complete user-facing changed-member identity
     */
    static String changedMember(final ChangePoint value) {
        if (value.getKind()
                == ChangePointKind.SERVICE_PROVIDER_REGISTRATION_REMOVED) {
            return serviceRegistration(value.getServiceRegistration()
                    .orElseThrow());
        }
        if (value.getName() == null) {
            return owner(value.getOwner());
        }
        final boolean field = value.getKind().name().startsWith("FIELD_");
        final String oldSignature = member(value.getOwner(), value.getName(),
                value.getOldDescriptor(), field);
        final String newSignature = member(value.getOwner(), value.getName(),
                value.getNewDescriptor(), field);
        if (value.getOldDescriptor() != null
                && value.getNewDescriptor() != null
                && !value.getOldDescriptor().equals(
                        value.getNewDescriptor())) {
            return oldSignature + " → " + newSignature;
        }
        return value.getNewDescriptor() == null
                ? oldSignature : newSignature;
    }

    /**
     * @param value structural metadata reference
     * @return complete class/member identity for structural metadata
     */
    static String structuralOwner(final StructuralReference value) {
        final String member = value.getReferencingMember();
        return owner(value.getReferencingClass())
                + (member.isBlank() ? "" : "#" + member);
    }

    private static String member(
            final String owner,
            final String name,
            final String descriptor,
            final boolean field) {
        final String suffix = descriptor == null ? ""
                : field ? ":" + descriptor : descriptor;
        return owner(owner) + "#" + name + suffix;
    }

    private static String serviceRegistration(
            final ServiceProviderRegistration value) {
        return "service " + owner(value.serviceInternalName())
                + " → provider " + owner(value.providerInternalName())
                + " [" + value.resourcePath() + "]";
    }

    private static String owner(final String value) {
        return value.replace('/', '.');
    }
}
