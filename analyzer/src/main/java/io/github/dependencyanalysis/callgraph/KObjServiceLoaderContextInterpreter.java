package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.FieldReference;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Strategy-owned ServiceLoader execution and IR cache. */
final class KObjServiceLoaderContextInterpreter
        implements SSAContextInterpreter {

    /** Pure summary IR creation. */
    private final KObjServiceLoaderSummaryIrFactory factory;

    /** Strategy-local generated IR cache. */
    private final Map<CGNode, IR> irs = new HashMap<>();

    KObjServiceLoaderContextInterpreter(
            final KObjServiceLoaderModel model) {
        factory = new KObjServiceLoaderSummaryIrFactory(model);
    }

    @Override
    public boolean understands(final CGNode node) {
        return factory.understands(node);
    }

    @Override
    public IR getIR(final CGNode node) {
        return irs.computeIfAbsent(node, factory::create);
    }

    @Override
    public Iterator<NewSiteReference> iterateNewSites(final CGNode node) {
        return getIR(node).iterateNewSites();
    }

    @Override
    public Iterator<FieldReference> iterateFieldsRead(final CGNode node) {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<FieldReference> iterateFieldsWritten(final CGNode node) {
        return Collections.emptyIterator();
    }

    @Override
    public boolean recordFactoryType(
            final CGNode node,
            final IClass klass) {
        return false;
    }

    @Override
    public Iterator<CallSiteReference> iterateCallSites(final CGNode node) {
        return getIR(node).iterateCallSites();
    }

    @Override
    public IRView getIRView(final CGNode node) {
        return getIR(node);
    }

    @Override
    public DefUse getDU(final CGNode node) {
        return new DefUse(getIR(node));
    }

    @Override
    public int getNumberOfStatements(final CGNode node) {
        return getIR(node).getInstructions().length;
    }

    @Override
    public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(
            final CGNode node) {
        return getIR(node).getControlFlowGraph();
    }
}
