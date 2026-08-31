package io.github.dependencyanalysis.callgraph.local;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.types.TypeReference;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Bounded definition-only constant recovery within one caller IR. */
public final class LocalConstantResolver {

    /** Constant kind requested by a supported protocol. */
    public enum ConstantKind {
        /** String constant. */
        STRING,
        /** Class metadata constant. */
        CLASS
    }

    /**
     * Resolves one value through constants, cast, pi and same-value phi.
     *
     * @param ir caller IR
     * @param value SSA value number
     * @param kind expected constant kind
     * @return exact or typed unresolved result
     */
    public LocalConstantResolution resolve(
            final IR ir,
            final int value,
            final ConstantKind kind) {
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(kind, "kind");
        return resolve(ir, new DefUse(ir), value, kind,
                new LinkedHashSet<>());
    }

    private LocalConstantResolution resolve(
            final IR ir,
            final DefUse defUse,
            final int value,
            final ConstantKind kind,
            final Set<Integer> visiting) {
        if (!visiting.add(value)) {
            return LocalConstantResolution.unresolved("cyclic-definition");
        }
        final LocalConstantResolution direct = direct(ir, value, kind);
        if (direct != null) {
            return direct;
        }
        final SSAInstruction definition = defUse.getDef(value);
        if (definition instanceof SSACheckCastInstruction cast) {
            return resolve(ir, defUse, cast.getVal(), kind, visiting);
        }
        if (definition instanceof SSAPiInstruction pi) {
            return resolve(ir, defUse, pi.getVal(), kind, visiting);
        }
        if (definition instanceof SSAPhiInstruction phi) {
            LocalConstantResolution merged = null;
            for (int index = 0; index < phi.getNumberOfUses(); index++) {
                final LocalConstantResolution candidate = resolve(
                        ir, defUse, phi.getUse(index), kind,
                        new LinkedHashSet<>(visiting));
                if (candidate.status()
                        != LocalConstantResolution.Status.RESOLVED) {
                    return LocalConstantResolution.unresolved(
                            "phi-input-unresolved");
                }
                if (merged != null && !sameValue(merged, candidate)) {
                    return LocalConstantResolution.unresolved(
                            "phi-inputs-differ");
                }
                merged = candidate;
            }
            return merged == null
                    ? LocalConstantResolution.unresolved("empty-phi")
                    : merged;
        }
        return LocalConstantResolution.unresolved(
                definition == null ? "parameter-or-unknown-definition"
                        : "unsupported-definition="
                        + definition.getClass().getSimpleName());
    }

    private LocalConstantResolution direct(
            final IR ir,
            final int value,
            final ConstantKind kind) {
        if (kind == ConstantKind.STRING
                && ir.getSymbolTable().isStringConstant(value)) {
            return LocalConstantResolution.string(
                    ir.getSymbolTable().getStringValue(value));
        }
        if (kind != ConstantKind.CLASS) {
            return null;
        }
        if (ir.getSymbolTable().isConstant(value)) {
            final TypeReference type = type(
                    ir.getSymbolTable().getConstantValue(value));
            if (type != null) {
                return LocalConstantResolution.type(type);
            }
        }
        final SSAInstruction definition = new DefUse(ir).getDef(value);
        if (definition instanceof SSALoadMetadataInstruction metadata) {
            final TypeReference type = type(metadata.getToken());
            if (type != null) {
                return LocalConstantResolution.type(type);
            }
        }
        return null;
    }

    private boolean sameValue(
            final LocalConstantResolution left,
            final LocalConstantResolution right) {
        return left.stringValue().equals(right.stringValue())
                && left.classValue().equals(right.classValue());
    }

    private TypeReference type(final Object value) {
        if (value instanceof IClass type) {
            return type.getReference();
        }
        return value instanceof TypeReference type ? type : null;
    }
}
