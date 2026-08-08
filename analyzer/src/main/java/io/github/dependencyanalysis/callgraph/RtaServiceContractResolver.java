package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.types.TypeReference;

import java.util.Iterator;

/** Resolves RTA ServiceLoader contracts from one caller's local IR only. */
final class RtaServiceContractResolver {

    TypeReference loadServiceType(
            final CGNode caller,
            final CallSiteReference site) {
        return loadServiceType(caller, invoke(caller, site));
    }

    TypeReference iteratorServiceType(
            final CGNode caller,
            final CallSiteReference site) {
        final SSAAbstractInvokeInstruction iteratorCall = invoke(caller, site);
        if (iteratorCall == null || iteratorCall.getNumberOfUses() == 0) {
            return null;
        }
        final SSAInstruction definition = new DefUse(caller.getIR())
                .getDef(iteratorCall.getReceiver());
        if (!(definition instanceof SSAAbstractInvokeInstruction load)
                || !ServiceLoaderProtocol.loadMethod(
                load.getDeclaredTarget())) {
            return null;
        }
        final TypeReference exact = loadServiceType(caller, load);
        return exact == null
                ? iteratorCheckcastServiceType(caller, iteratorCall) : exact;
    }

    private TypeReference iteratorCheckcastServiceType(
            final CGNode caller,
            final SSAAbstractInvokeInstruction iteratorCall) {
        if (iteratorCall.getNumberOfDefs() == 0) {
            return null;
        }
        final DefUse defUse = new DefUse(caller.getIR());
        TypeReference result = null;
        final Iterator<SSAInstruction> iteratorUses = defUse.getUses(
                iteratorCall.getDef());
        while (iteratorUses.hasNext()) {
            final SSAInstruction use = iteratorUses.next();
            if (!(use instanceof SSAAbstractInvokeInstruction next)
                    || next.getNumberOfDefs() == 0
                    || !"java/util/Iterator".equals(owner(
                    next.getDeclaredTarget().getDeclaringClass()))
                    || !"next".equals(next.getDeclaredTarget()
                    .getName().toString())) {
                continue;
            }
            final Iterator<SSAInstruction> nextUses = defUse.getUses(
                    next.getDef());
            while (nextUses.hasNext()) {
                final SSAInstruction nextUse = nextUses.next();
                if (!(nextUse instanceof SSACheckCastInstruction cast)) {
                    continue;
                }
                final TypeReference[] declared =
                        cast.getDeclaredResultTypes();
                if (declared.length != 1) {
                    return null;
                }
                final TypeReference candidate = declared[0];
                if (candidate == null || result != null
                        && !result.equals(candidate)) {
                    return null;
                }
                result = candidate;
            }
        }
        return result;
    }

    private TypeReference loadServiceType(
            final CGNode caller,
            final SSAAbstractInvokeInstruction invoke) {
        if (invoke == null || !ServiceLoaderProtocol.loadMethod(
                invoke.getDeclaredTarget())
                || invoke.getNumberOfUses() == 0) {
            return null;
        }
        final IR ir = caller.getIR();
        final int value = invoke.getUse(0);
        if (ir.getSymbolTable().isConstant(value)) {
            return metadataType(ir.getSymbolTable().getConstantValue(value));
        }
        final SSAInstruction definition = new DefUse(ir).getDef(value);
        return definition instanceof SSALoadMetadataInstruction metadata
                ? metadataType(metadata.getToken()) : null;
    }

    private SSAAbstractInvokeInstruction invoke(
            final CGNode caller,
            final CallSiteReference site) {
        final IR ir = caller.getIR();
        if (ir == null || ir.getCallInstructionIndices(site) == null) {
            return null;
        }
        for (SSAAbstractInvokeInstruction invoke : ir.getCalls(site)) {
            return invoke;
        }
        return null;
    }

    private TypeReference metadataType(final Object value) {
        if (value instanceof IClass type) {
            return type.getReference();
        }
        return value instanceof TypeReference type ? type : null;
    }

    private String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }
}
