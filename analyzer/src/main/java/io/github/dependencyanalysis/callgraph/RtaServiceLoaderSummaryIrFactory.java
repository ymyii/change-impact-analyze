package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;

/** Creates RTA ServiceLoader reachability IR without return carriers. */
final class RtaServiceLoaderSummaryIrFactory {

    /** RTA-owned protocol summaries and synthetic iterator classes. */
    private final RtaServiceLoaderModel model;

    RtaServiceLoaderSummaryIrFactory(final RtaServiceLoaderModel value) {
        model = Objects.requireNonNull(value, "value");
    }

    boolean understands(final CGNode node) {
        return model.serviceType(node) != null
                && (ServiceLoaderProtocol.loadMethod(
                node.getMethod().getReference())
                || ServiceLoaderProtocol.iteratorMethod(
                node.getMethod().getReference()));
    }

    IR create(final CGNode node) {
        final TypeReference service = model.serviceType(node);
        final MethodReference reference = node.getMethod().getReference();
        final MethodSummary summary = new MethodSummary(reference);
        final SSAInstructionFactory instructions = node.getMethod()
                .getDeclaringClass().getClassLoader().getLanguage()
                .instructionFactory();
        final int allocation = reference.getNumberOfParameters() + 2;
        final TypeReference allocated;
        if (ServiceLoaderProtocol.loadMethod(reference)) {
            summary.setStatic(true);
            allocated = reference.getReturnType();
        } else {
            allocated = model.iteratorType(service);
        }
        summary.addStatement(instructions.NewInstruction(0, allocation,
                NewSiteReference.make(0, allocated)));
        final int result = allocation + 1;
        summary.addConstant(result, new ConstantValue(null));
        summary.addStatement(instructions.ReturnInstruction(
                1, result, false));
        return new SummarizedMethod(reference, summary,
                node.getMethod().getDeclaringClass()).makeIR(
                node.getContext(), SSAOptions.defaultOptions());
    }
}
