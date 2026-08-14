package io.github.dependencyanalysis.callgraph.protocol.serviceloader;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.util.List;

/** Stateless instruction emission used by strategy-specific models. */
public final class ServiceLoaderProviderSummaryMethods {

    private ServiceLoaderProviderSummaryMethods() {
    }

    /**
     * Creates the provider-allocation summary for iterator next.
     *
     * @param iterator synthetic iterator type
     * @param declaringClass synthetic declaring class
     * @param providers configured providers
     * @return summarized next method
     */
    public static IMethod next(
            final TypeReference iterator,
            final IClass declaringClass,
            final List<ServiceLoaderProviderDefinition> providers) {
        final MethodReference reference = MethodReference.findOrCreate(
                iterator, Selector.make("next()Ljava/lang/Object;"));
        final MethodSummary summary = new MethodSummary(reference);
        final SSAInstructionFactory instructions = declaringClass
                .getClassLoader().getInstructionFactory();
        int instructionIndex = 0;
        int value = 2;
        final int[] instances = new int[providers.size()];
        for (int position = 0; position < providers.size(); position++) {
            final ServiceLoaderProviderDefinition provider =
                    providers.get(position);
            final int instance = value++;
            instances[position] = instance;
            summary.addStatement(instructions.NewInstruction(
                    instructionIndex, instance, NewSiteReference.make(
                            instructionIndex,
                            provider.type().getReference())));
            instructionIndex++;
            summary.addStatement(instructions.InvokeInstruction(
                    instructionIndex, new int[]{instance}, value++,
                    CallSiteReference.make(instructionIndex,
                            provider.constructor().getReference(),
                            Dispatch.SPECIAL), null));
            instructionIndex++;
        }
        final int result;
        if (instances.length == 0) {
            result = value;
            summary.addConstant(result, new ConstantValue(null));
        } else if (instances.length == 1) {
            result = instances[0];
        } else {
            result = value;
            summary.addStatement(instructions.PhiInstruction(
                    instructionIndex++, result, instances));
        }
        summary.addStatement(instructions.ReturnInstruction(
                instructionIndex, result, false));
        return new SummarizedMethod(reference, summary, declaringClass);
    }

    /**
     * Creates the provider-availability summary.
     *
     * @param iterator synthetic iterator type
     * @param declaringClass synthetic declaring class
     * @return summarized hasNext method
     */
    public static IMethod hasNext(
            final TypeReference iterator,
            final IClass declaringClass) {
        final MethodReference reference = MethodReference.findOrCreate(
                iterator, Selector.make("hasNext()Z"));
        final MethodSummary summary = new MethodSummary(reference);
        summary.addConstant(2, new ConstantValue(1));
        summary.addStatement(declaringClass.getClassLoader()
                .getInstructionFactory().ReturnInstruction(0, 2, true));
        return new SummarizedMethod(reference, summary, declaringClass);
    }
}
