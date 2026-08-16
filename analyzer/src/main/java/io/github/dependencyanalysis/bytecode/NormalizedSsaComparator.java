package io.github.dependencyanalysis.bytecode;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.TypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative alpha-normalized WALA SSA/CFG comparator. */
final class NormalizedSsaComparator {

    /**
     * Compares two WALA IR instances within the supported model.
     *
     * @param oldIr baseline IR
     * @param newIr target IR
     * @return comparison outcome
     */
    SsaComparisonOutcome compare(final IR oldIr, final IR newIr) {
        if (oldIr == null || newIr == null) {
            return unknown("IR_MISSING");
        }
        try {
            final NormalizedMethod oldMethod = normalize(oldIr);
            final NormalizedMethod newMethod = normalize(newIr);
            if (oldMethod.equals(newMethod)) {
                return new SsaComparisonOutcome(
                        SsaComparisonStatus.MATCHED,
                        "NORMALIZED_SSA_CFG_ISOMORPHIC");
            }
            return new SsaComparisonOutcome(
                    SsaComparisonStatus.DIFFERENT,
                    "NORMALIZED_SSA_CFG_DIFFERENT");
        } catch (UnsupportedSsaException exception) {
            return unknown(exception.getMessage());
        } catch (RuntimeException exception) {
            return unknown("SSA_NORMALIZATION_FAILED:"
                    + exception.getClass().getSimpleName());
        }
    }

    /**
     * Renders the canonical normalized method used by this comparator.
     *
     * @param ir WALA IR
     * @return deterministic normalized text
     */
    String renderNormalized(final IR ir) {
        if (ir == null) {
            throw new UnsupportedSsaException("IR_MISSING");
        }
        return normalize(ir).render();
    }

    private SsaComparisonOutcome unknown(final String reason) {
        return new SsaComparisonOutcome(SsaComparisonStatus.UNKNOWN, reason);
    }

    private NormalizedMethod normalize(final IR ir) {
        final List<BlockModel> blocks = blocks(ir);
        final List<SSAInstruction> instructions = new ArrayList<>();
        final Map<SSAInstruction, Integer> instructionIds =
                new IdentityHashMap<>();
        for (BlockModel block : blocks) {
            for (SSAInstruction instruction : block.instructions()) {
                instructionIds.computeIfAbsent(instruction, ignored -> {
                    final int id = instructions.size();
                    instructions.add(instruction);
                    return id;
                });
            }
        }
        final Map<Integer, String> values = valueNames(
                ir.getSymbolTable(), instructions);
        final List<String> normalizedInstructions = new ArrayList<>();
        for (SSAInstruction instruction : instructions) {
            normalizedInstructions.add(instruction(
                    instruction, values, instructionIds));
        }
        final List<String> normalizedBlocks = new ArrayList<>();
        for (BlockModel block : blocks) {
            final List<Integer> ids = block.instructions().stream()
                    .map(instructionIds::get).toList();
            final List<String> catches = iteratorList(
                    block.block().getCaughtExceptionTypes()).stream()
                    .map(TypeReference::toString).sorted().toList();
            final List<String> successors = successors(
                    ir, block.block(), blocks, instructionIds);
            normalizedBlocks.add(ids + "|catch=" + catches
                    + "|successors=" + successors);
        }
        return new NormalizedMethod(normalizedInstructions,
                normalizedBlocks);
    }

    private List<BlockModel> blocks(final IR ir) {
        final List<BlockModel> result = new ArrayList<>();
        for (ISSABasicBlock block : ir.getControlFlowGraph()) {
            final Set<SSAInstruction> values =
                    java.util.Collections.newSetFromMap(
                            new IdentityHashMap<>());
            final List<SSAInstruction> instructions = new ArrayList<>();
            add(iteratorList(block.iteratePhis()), values, instructions);
            add(iteratorList(block.iteratePis()), values, instructions);
            add(iteratorList(block.iterator()), values, instructions);
            instructions.removeIf(value -> value instanceof SSAGotoInstruction);
            result.add(new BlockModel(block, List.copyOf(instructions)));
        }
        return result;
    }

    private void add(
            final List<? extends SSAInstruction> source,
            final Set<SSAInstruction> seen,
            final List<SSAInstruction> target) {
        for (SSAInstruction instruction : source) {
            if (instruction != null && seen.add(instruction)) {
                target.add(instruction);
            }
        }
    }

    private Map<Integer, String> valueNames(
            final SymbolTable symbols,
            final List<SSAInstruction> instructions) {
        final Map<Integer, String> result = new HashMap<>();
        final int[] parameters = symbols.getParameterValueNumbers();
        for (int index = 0; index < parameters.length; index++) {
            result.put(parameters[index], "P" + index);
        }
        for (int value = 1; value <= symbols.getMaxValueNumber(); value++) {
            if (symbols.isConstant(value)) {
                result.put(value, constant(symbols.getConstantValue(value)));
            }
        }
        for (int index = 0; index < instructions.size(); index++) {
            final SSAInstruction instruction = instructions.get(index);
            for (int def = 0;
                    def < instruction.getNumberOfDefs(); def++) {
                result.put(instruction.getDef(def),
                        "D" + index + "." + def);
            }
        }
        return result;
    }

    private String instruction(
            final SSAInstruction value,
            final Map<Integer, String> values,
            final Map<SSAInstruction, Integer> instructionIds) {
        final StringBuilder result = new StringBuilder(
                value.getClass().getName());
        semanticAttributes(value, result, instructionIds);
        result.append("|defs=");
        for (int index = 0; index < value.getNumberOfDefs(); index++) {
            result.append(requireValue(values, value.getDef(index)))
                    .append(',');
        }
        result.append("|uses=");
        for (int index = 0; index < value.getNumberOfUses(); index++) {
            result.append(index).append('=')
                    .append(requireValue(values, value.getUse(index)))
                    .append(',');
        }
        result.append("|pei=").append(value.isPEI())
                .append("|fallthrough=").append(value.isFallThrough())
                .append("|exceptions=")
                .append(value.getExceptionTypes().stream()
                        .map(TypeReference::toString)
                        .sorted().toList());
        return result.toString();
    }

    private void semanticAttributes(
            final SSAInstruction value,
            final StringBuilder result,
            final Map<SSAInstruction, Integer> instructionIds) {
        if (value instanceof SSAInvokeDynamicInstruction) {
            throw new UnsupportedSsaException(
                    "BOOTSTRAP_EVIDENCE_INSUFFICIENT");
        } else if (value instanceof SSAAbstractInvokeInstruction invoke) {
            result.append("|target=").append(invoke.getDeclaredTarget())
                    .append("|dispatch=").append(invoke.getInvocationCode())
                    .append("|returns=")
                    .append(invoke.getNumberOfReturnValues());
        } else if (value instanceof SSAFieldAccessInstruction field) {
            result.append("|field=").append(field.getDeclaredField())
                    .append("|static=").append(field.isStatic());
        } else if (value instanceof SSANewInstruction instruction) {
            result.append("|type=").append(instruction.getConcreteType());
        } else if (value instanceof SSACheckCastInstruction instruction) {
            result.append("|types=").append(List.of(
                    instruction.getDeclaredResultTypes()));
        } else if (value instanceof SSAInstanceofInstruction instruction) {
            result.append("|type=").append(instruction.getCheckedType());
        } else if (value instanceof SSAArrayReferenceInstruction instruction) {
            result.append("|element=").append(instruction.getElementType());
        } else if (value instanceof SSALoadMetadataInstruction metadata) {
            result.append("|type=").append(metadata.getType())
                    .append("|token=").append(constant(metadata.getToken()));
        } else if (value instanceof SSABinaryOpInstruction instruction) {
            result.append("|operator=").append(instruction.getOperator());
        } else if (value instanceof SSAPiInstruction pi) {
            final Integer cause = instructionIds.get(pi.getCause());
            if (cause == null) {
                throw new UnsupportedSsaException("UNMAPPED_PI_CAUSE");
            }
            result.append("|cause=D").append(cause)
                    .append("|causeType=")
                    .append(pi.getCause().getClass().getName());
        } else if (value instanceof SSAUnaryOpInstruction instruction) {
            result.append("|operator=").append(instruction.getOpcode());
        } else if (value instanceof SSAComparisonInstruction instruction) {
            result.append("|operator=").append(instruction.getOperator());
        } else if (value
                instanceof SSAConditionalBranchInstruction branch) {
            result.append("|operator=").append(branch.getOperator())
                    .append("|type=").append(branch.getType());
        } else if (value instanceof SSASwitchInstruction instruction) {
            result.append("|cases=").append(java.util.Arrays.toString(
                            instruction.getCasesAndLabels()))
                    .append("|default=").append(instruction.getDefault());
        } else if (value instanceof SSAConversionInstruction instruction) {
            result.append("|from=").append(instruction.getFromType())
                    .append("|to=").append(instruction.getToType());
        } else if (value instanceof SSAReturnInstruction instruction) {
            result.append("|void=").append(instruction.returnsVoid())
                    .append("|primitive=")
                    .append(instruction.returnsPrimitiveType());
        } else if (value instanceof SSAMonitorInstruction instruction) {
            result.append("|enter=").append(instruction.isMonitorEnter());
        } else if (value instanceof SSAPhiInstruction
                || value instanceof SSAGetCaughtExceptionInstruction
                || value instanceof SSAThrowInstruction) {
            result.append("|intrinsic=DEF_USE_CFG_CATCH");
        } else {
            throw new UnsupportedSsaException(
                    "UNSUPPORTED_INSTRUCTION:"
                            + value.getClass().getName());
        }
    }

    private List<String> successors(
            final IR ir,
            final ISSABasicBlock block,
            final List<BlockModel> blocks,
            final Map<SSAInstruction, Integer> instructionIds) {
        final List<String> result = new ArrayList<>();
        final BlockModel current = blocks.stream()
                .filter(value -> value.block().equals(block))
                .findFirst().orElseThrow();
        final SSAInstruction last = current.instructions().isEmpty()
                ? null : current.instructions().get(
                current.instructions().size() - 1);
        final ISSABasicBlock branchTarget = last
                instanceof SSAConditionalBranchInstruction branch
                ? ir.getControlFlowGraph().getBlockForInstruction(
                branch.getTarget()) : null;
        final Iterator<ISSABasicBlock> iterator =
                ir.getControlFlowGraph().getSuccNodes(block);
        while (iterator.hasNext()) {
            final ISSABasicBlock successor = iterator.next();
            final String kind;
            if (ir.getControlFlowGraph().getExceptionalSuccessors(block)
                    .contains(successor)) {
                kind = "E";
            } else if (branchTarget != null) {
                kind = branchTarget.equals(successor) ? "T" : "F";
            } else {
                kind = "N";
            }
            result.add(kind + ":" + blockIdentity(
                    ir, successor, blocks, instructionIds,
                    new LinkedHashSet<>()));
        }
        result.sort(String::compareTo);
        return result;
    }

    private String blockIdentity(
            final IR ir,
            final ISSABasicBlock block,
            final List<BlockModel> blocks,
            final Map<SSAInstruction, Integer> instructionIds,
            final Set<ISSABasicBlock> visited) {
        if (!visited.add(block)) {
            return "CYCLE";
        }
        final BlockModel model = blocks.stream()
                .filter(value -> value.block().equals(block))
                .findFirst().orElseThrow();
        if (!model.instructions().isEmpty()) {
            return "I" + instructionIds.get(model.instructions().get(0));
        }
        if (block.isExitBlock()) {
            return "EXIT";
        }
        final List<String> targets = new ArrayList<>();
        final Iterator<ISSABasicBlock> successors = ir
                .getControlFlowGraph().getSuccNodes(block);
        while (successors.hasNext()) {
            targets.add(blockIdentity(ir, successors.next(), blocks,
                    instructionIds, new LinkedHashSet<>(visited)));
        }
        targets.sort(String::compareTo);
        return "EMPTY" + targets;
    }

    private String requireValue(
            final Map<Integer, String> values, final int value) {
        final String result = values.get(value);
        if (result == null) {
            throw new UnsupportedSsaException(
                    "UNMAPPED_SSA_VALUE:" + value);
        }
        return result;
    }

    private String constant(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Float number) {
            return "Float:" + Float.floatToRawIntBits(number);
        }
        if (value instanceof Double number) {
            return "Double:" + Double.doubleToRawLongBits(number);
        }
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof TypeReference) {
            return value.getClass().getName() + ":" + value;
        }
        throw new UnsupportedSsaException(
                "UNSUPPORTED_CONSTANT:" + value.getClass().getName());
    }

    private <T> List<T> iteratorList(final Iterator<T> iterator) {
        final List<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    /**
     * Normalized method model.
     *
     * @param instructions normalized instructions
     * @param blocks normalized blocks
     */
    private record NormalizedMethod(
            List<String> instructions,
            List<String> blocks) {

        String render() {
            final StringBuilder result = new StringBuilder();
            result.append("instructions:\n");
            for (int index = 0; index < instructions.size(); index++) {
                result.append('I').append(index).append('=')
                        .append(instructions.get(index)).append('\n');
            }
            result.append("blocks:\n");
            for (int index = 0; index < blocks.size(); index++) {
                result.append('B').append(index).append('=')
                        .append(blocks.get(index)).append('\n');
            }
            return result.toString();
        }
    }

    /**
     * Block and its semantic instructions.
     *
     * @param block WALA basic block
     * @param instructions semantic instructions
     */
    private record BlockModel(
            ISSABasicBlock block,
            List<SSAInstruction> instructions) {
    }

    /** Unsupported evidence that must retain the raw ChangePoint. */
    private static final class UnsupportedSsaException
            extends RuntimeException {

        /** @param reason stable reason */
        UnsupportedSsaException(final String reason) {
            super(reason);
        }
    }
}
