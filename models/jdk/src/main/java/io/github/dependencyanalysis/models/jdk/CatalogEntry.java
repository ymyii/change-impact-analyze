package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.List;

/**
 * One exact public JDK method and its summary semantics.
 *
 * @param owner internal JVM owner name
 * @param name method name
 * @param descriptor JVM method descriptor
 * @param staticMethod whether the target is static
 * @param template Synthetic IR template
 * @param dataArgument explicit data argument index
 * @param slot input or mutation state slot
 * @param callbacks application callback specifications
 * @param resultSlot callback or copy result state slot
 */
record CatalogEntry(
        String owner,
        String name,
        String descriptor,
        boolean staticMethod,
        SummaryTemplate template,
        int dataArgument,
        StateSlot slot,
        List<CallbackSpec> callbacks,
        StateSlot resultSlot) {

    MethodReference reference() {
        try {
            final TypeReference type = TypeReference.findOrCreate(
                    ClassLoaderReference.Primordial, "L" + owner);
            return MethodReference.findOrCreate(type,
                    Atom.findOrCreateAsciiAtom(name),
                    Descriptor.findOrCreateUTF8(descriptor));
        } catch (IllegalArgumentException exception) {
            throw new JdkModelException(
                    "Invalid catalog method: " + owner + "." + name
                            + descriptor,
                    exception);
        }
    }

    String identity() {
        return owner + "." + name + descriptor;
    }
}
