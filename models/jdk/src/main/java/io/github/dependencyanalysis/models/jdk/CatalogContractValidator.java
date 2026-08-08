package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.XMLMethodSummaryReader;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates resolved catalog, callback and WALA native contracts. */
final class CatalogContractValidator {

    /** Callback argument token prefix length. */
    private static final int ARG_PREFIX_LENGTH = "arg:".length();

    /** Active hierarchy. */
    private final IClassHierarchy hierarchy;

    CatalogContractValidator(final IClassHierarchy activeHierarchy) {
        hierarchy = activeHierarchy;
    }

    void validateNativeConflicts(final List<CatalogEntry> entries) {
        final Set<MethodReference> nativeTargets = nativeTargets();
        final List<String> conflicts = entries.stream()
                .map(CatalogEntry::reference)
                .filter(nativeTargets::contains)
                .map(MethodReference::toString)
                .sorted().toList();
        if (!conflicts.isEmpty()) {
            throw new JdkModelException(
                    "Catalog conflicts with WALA native summaries: "
                            + conflicts);
        }
    }

    void validateResolved(
            final CatalogEntry entry,
            final IMethod resolved) {
        if (resolved.isStatic() != entry.staticMethod()) {
            throw new JdkModelException(
                    "Catalog static contract mismatch: "
                            + entry.identity());
        }
        for (CallbackSpec callback : entry.callbacks()) {
            validateCallback(entry, callback);
        }
    }

    private void validateCallback(
            final CatalogEntry entry,
            final CallbackSpec callback) {
        validateArgument(entry, callback.receiverArgument(),
                "callback receiver");
        for (String input : callback.inputs()) {
            if (input.startsWith("arg:")) {
                validateArgument(entry, Integer.parseInt(
                        input.substring(ARG_PREFIX_LENGTH)),
                        "callback input");
            }
        }
        final TypeReference owner = TypeReference.findOrCreate(
                ClassLoaderReference.Primordial,
                "L" + callback.owner());
        final IClass ownerClass = hierarchy.lookupClass(owner);
        final MethodReference target = MethodReference.findOrCreate(
                owner, Selector.make(
                        callback.name() + callback.descriptor()));
        final IMethod method = ownerClass == null ? null
                : ownerClass.getAllMethods().stream()
                        .filter(candidate -> candidate.getSelector()
                                .equals(target.getSelector()))
                        .findFirst().orElse(null);
        if (ownerClass == null || method == null || method.isStatic()) {
            throw new JdkModelException(
                    "Catalog callback target is unresolved: " + target
                            + " for " + entry.identity());
        }
        if (callback.dispatch() == Dispatch.INTERFACE
                && !ownerClass.isInterface()) {
            throw new JdkModelException(
                    "INTERFACE callback owner is not an interface: "
                            + target);
        }
    }

    private void validateArgument(
            final CatalogEntry entry,
            final int argument,
            final String role) {
        if (argument < 0
                || argument >= entry.reference().getNumberOfParameters()) {
            throw new JdkModelException(
                    "Invalid " + role + " argument " + argument
                            + " for " + entry.identity());
        }
    }

    private Set<MethodReference> nativeTargets() {
        final String resource = Util.getNativeSpec();
        try (InputStream input = Util.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new JdkModelException(
                        "WALA native summary resource is unavailable: "
                                + resource);
            }
            final XMLMethodSummaryReader reader =
                    new XMLMethodSummaryReader(input, hierarchy.getScope());
            return new HashSet<>(reader.getSummaries().keySet());
        } catch (IOException exception) {
            throw new JdkModelException(
                    "Unable to close WALA native summary resource",
                    exception);
        }
    }
}
