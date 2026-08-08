package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;

import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Collects immutable reachable caller/IR instruction facts once per query. */
final class ReachableReferenceCollector {

    /** Stable reachable instruction facts. */
    private final List<ReachableInstruction> instructions;

    ReachableReferenceCollector(final ModuleCallGraphSession session) {
        Objects.requireNonNull(session, "session");
        final ArrayList<ReachableInstruction> values = new ArrayList<>();
        for (CGNode node : session.getGraph()) {
            if (node.equals(session.getGraph().getFakeRootNode())
                    || node.equals(
                    session.getGraph().getFakeWorldClinitNode())) {
                continue;
            }
            final IR ir = node.getIR();
            if (ir == null) {
                continue;
            }
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction != null) {
                    values.add(new ReachableInstruction(node, instruction));
                }
            }
        }
        instructions = List.copyOf(values);
    }

    /** @return immutable reachable caller/instruction facts */
    List<ReachableInstruction> instructions() {
        return instructions;
    }

    /**
     * Reachable instruction with its exact caller Context.
     *
     * @param caller reachable caller
     * @param instruction caller IR instruction
     */
    record ReachableInstruction(
            CGNode caller,
            SSAInstruction instruction) {

        ReachableInstruction {
            Objects.requireNonNull(caller, "caller");
            Objects.requireNonNull(instruction, "instruction");
        }
    }
}
