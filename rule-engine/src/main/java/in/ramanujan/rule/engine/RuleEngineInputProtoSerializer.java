package in.ramanujan.rule.engine;

import in.ramanujan.pojo.RuleEngineInput;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.*;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.ArrayCommand;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.RedefineArrayCommand;
import in.ramanujan.rule.engine.proto.RuleEngineProto;

import java.util.List;
import java.util.Map;

public final class RuleEngineInputProtoSerializer {

    private RuleEngineInputProtoSerializer() {}

    public static byte[] serialize(RuleEngineInput input) {
        RuleEngineProto.RuleEngineInput.Builder b = RuleEngineProto.RuleEngineInput.newBuilder();

        if (input.getVariables() != null) {
            for (Variable v : input.getVariables()) b.addVariables(toProto(v));
        }
        if (input.getCommands() != null) {
            for (Command c : input.getCommands()) b.addCommands(toProto(c));
        }
        if (input.getIfBlocks() != null) {
            for (If i : input.getIfBlocks()) b.addIfBlocks(toProto(i));
        }
        if (input.getOperations() != null) {
            for (Operation o : input.getOperations()) b.addOperations(toProto(o));
        }
        if (input.getConditions() != null) {
            for (Condition c : input.getConditions()) b.addConditions(toProto(c));
        }
        if (input.getConstants() != null) {
            for (Constant c : input.getConstants()) b.addConstants(toProto(c));
        }
        if (input.getArrays() != null) {
            for (Array a : input.getArrays()) b.addArrays(toProto(a));
        }
        if (input.getMethodDataTypeAgnosticArgs() != null) {
            for (MethodDataTypeAgnosticArg m : input.getMethodDataTypeAgnosticArgs()) b.addMethodDataTypeAgnosticArgs(toProto(m));
        }
        if (input.getFunctionCalls() != null) {
            for (FunctionCall fc : input.getFunctionCalls()) b.addFunctionCalls(toProto(fc));
        }
        if (input.getWhileBlocks() != null) {
            for (While w : input.getWhileBlocks()) b.addWhileBlocks(toProto(w));
        }
        if (input.getRedefineArrayCommands() != null) {
            for (RedefineArrayCommand r : input.getRedefineArrayCommands()) b.addRedefineArrayCommands(toProto(r));
        }
        if (input.getReturnOperations() != null) {
            for (ReturnOperation r : input.getReturnOperations()) b.addReturnOperations(toProto(r));
        }

        return b.build().toByteArray();
    }

    private static RuleEngineProto.Variable toProto(Variable v) {
        RuleEngineProto.Variable.Builder b = RuleEngineProto.Variable.newBuilder();
        if (v.getId() != null) b.setId(v.getId());
        if (v.getCodeStrPtr() != null) b.setCodeStrPtr(v.getCodeStrPtr());
        if (v.getName() != null) b.setName(v.getName());
        if (v.getDataType() != null) b.setDataType(v.getDataType());
        if (v.getValue() instanceof Number) b.setValue(((Number) v.getValue()).doubleValue());
        b.setIsReturnable(v.isReturnable());
        if (v.getFrameCount() != null) b.setFrameCount(String.valueOf(v.getFrameCount()));
        return b.build();
    }

    private static RuleEngineProto.MethodDataTypeAgnosticArg toProto(MethodDataTypeAgnosticArg m) {
        RuleEngineProto.MethodDataTypeAgnosticArg.Builder b = RuleEngineProto.MethodDataTypeAgnosticArg.newBuilder();
        if (m.getId() != null) b.setId(m.getId());
        if (m.name != null) b.setName(m.name);
        b.setFrameCount(String.valueOf(m.frameCount));
        return b.build();
    }

    private static RuleEngineProto.Command toProto(Command c) {
        RuleEngineProto.Command.Builder b = RuleEngineProto.Command.newBuilder();
        if (c.getId() != null) b.setId(c.getId());
        if (c.getCodeStrPtr() != null) b.setCodeStrPtr(c.getCodeStrPtr());
        if (c.getImmediateParentRuleEngineInputUnitId() != null) b.setImmediateParentRuleEngineInputUnitId(c.getImmediateParentRuleEngineInputUnitId());
        if (c.getNextId() != null) b.setNextId(c.getNextId());
        if (c.getIfBlocks() != null) b.setIfBlocks(c.getIfBlocks());
        if (c.getLoops() != null) b.setLoops(c.getLoops());
        if (c.getOperation() != null) b.setOperation(c.getOperation());
        if (c.getConstant() != null) b.setConstant(c.getConstant());
        if (c.getVariableId() != null) b.setVariableId(c.getVariableId());
        if (c.getConditionId() != null) b.setConditionId(c.getConditionId());
        if (c.getWhileId() != null) b.setWhileId(c.getWhileId());
        if (c.getReturnOperation() != null) b.setReturnOperation(c.getReturnOperation());
        if (c.getReturnStatement() != null) b.setReturnStatement(c.getReturnStatement());
        if (c.getNextDagTriggerIds() != null) b.addAllNextDagTriggerIds(c.getNextDagTriggerIds());
        if (c.getFunctionCall() != null) b.setFunctionCall(toProto(c.getFunctionCall()));
        if (c.getArrayCommand() != null) b.setArrayCommand(toProto(c.getArrayCommand()));
        if (c.getRedefineArrayCommand() != null) b.setRedefineArrayCommand(toProto(c.getRedefineArrayCommand()));
        if (c.getReturnAssignmentPairs() != null) {
            for (ReturnAssignmentPair p : c.getReturnAssignmentPairs()) b.addReturnAssignmentPairs(toProto(p));
        }
        return b.build();
    }

    private static RuleEngineProto.ArrayCommand toProto(ArrayCommand ac) {
        RuleEngineProto.ArrayCommand.Builder b = RuleEngineProto.ArrayCommand.newBuilder();
        if (ac.getArrayId() != null) b.setArrayId(ac.getArrayId());
        if (ac.getIndex() != null) b.addAllIndex(ac.getIndex());
        return b.build();
    }

    private static RuleEngineProto.ReturnAssignmentPair toProto(ReturnAssignmentPair p) {
        RuleEngineProto.ReturnAssignmentPair.Builder b = RuleEngineProto.ReturnAssignmentPair.newBuilder();
        if (p.getTargetCommandId() != null) b.setTargetCommandId(p.getTargetCommandId());
        if (p.getSourceCommandId() != null) b.setSourceCommandId(p.getSourceCommandId());
        return b.build();
    }

    private static RuleEngineProto.IfBlock toProto(If i) {
        RuleEngineProto.IfBlock.Builder b = RuleEngineProto.IfBlock.newBuilder();
        if (i.getId() != null) b.setId(i.getId());
        if (i.getCodeStrPtr() != null) b.setCodeStrPtr(i.getCodeStrPtr());
        if (i.getImmediateParentRuleEngineInputUnitId() != null) b.setImmediateParentRuleEngineInputUnitId(i.getImmediateParentRuleEngineInputUnitId());
        if (i.getConditionId() != null) b.setConditionId(i.getConditionId());
        if (i.getIfCommand() != null) b.setIfCommand(i.getIfCommand());
        if (i.getElseCommandId() != null) b.setElseCommand(i.getElseCommandId());
        return b.build();
    }

    private static RuleEngineProto.Operation toProto(Operation o) {
        RuleEngineProto.Operation.Builder b = RuleEngineProto.Operation.newBuilder();
        if (o.getId() != null) b.setId(o.getId());
        if (o.getCodeStrPtr() != null) b.setCodeStrPtr(o.getCodeStrPtr());
        if (o.getOperatorType() != null) b.setOperatorType(o.getOperatorType());
        if (o.getOperand1() != null) b.setOperand1(o.getOperand1());
        if (o.getOperand2() != null) b.setOperand2(o.getOperand2());
        return b.build();
    }

    private static RuleEngineProto.Condition toProto(Condition c) {
        RuleEngineProto.Condition.Builder b = RuleEngineProto.Condition.newBuilder();
        if (c.getId() != null) b.setId(c.getId());
        if (c.getCodeStrPtr() != null) b.setCodeStrPtr(c.getCodeStrPtr());
        if (c.getConditionType() != null) b.setConditionType(c.getConditionType());
        if (c.getComparisionCommand1() != null) b.setComparisionCommand1(c.getComparisionCommand1());
        if (c.getComparisionCommand2() != null) b.setComparisionCommand2(c.getComparisionCommand2());
        return b.build();
    }

    private static RuleEngineProto.Constant toProto(Constant c) {
        RuleEngineProto.Constant.Builder b = RuleEngineProto.Constant.newBuilder();
        if (c.getId() != null) b.setId(c.getId());
        if (c.getCodeStrPtr() != null) b.setCodeStrPtr(c.getCodeStrPtr());
        if (c.getDataType() != null) b.setDataType(c.getDataType());
        if (c.getValue() instanceof Number) b.setValue(((Number) c.getValue()).doubleValue());
        return b.build();
    }

    private static RuleEngineProto.Array toProto(Array a) {
        RuleEngineProto.Array.Builder b = RuleEngineProto.Array.newBuilder();
        if (a.getId() != null) b.setId(a.getId());
        if (a.getCodeStrPtr() != null) b.setCodeStrPtr(a.getCodeStrPtr());
        if (a.getDataType() != null) b.setDataType(a.getDataType());
        if (a.getName() != null) b.setName(a.getName());
        if (a.getFrameCount() != null) b.setFrameCount(String.valueOf(a.getFrameCount()));
        if (a.getDimension() != null) b.addAllDimension(a.getDimension());
        if (a.getBinaryFile() != null) {
            b.setBinaryFile(a.getBinaryFile());
        } else if (a.getValues() != null) {
            for (Map.Entry<String, Object> entry : a.getValues().entrySet()) {
                if (entry.getValue() instanceof Number) {
                    b.putValues(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
            }
        }
        return b.build();
    }

    private static RuleEngineProto.FunctionCall toProto(FunctionCall fc) {
        RuleEngineProto.FunctionCall.Builder b = RuleEngineProto.FunctionCall.newBuilder();
        if (fc.getId() != null) b.setId(fc.getId());
        if (fc.getCodeStrPtr() != null) b.setCodeStrPtr(fc.getCodeStrPtr());
        if (fc.getImmediateParentRuleEngineInputUnitId() != null) b.setImmediateParentRuleEngineInputUnitId(fc.getImmediateParentRuleEngineInputUnitId());
        if (fc.getFirstCommandId() != null) b.setFirstCommandId(fc.getFirstCommandId());
        if (fc.getArguments() != null) b.addAllArguments(fc.getArguments());
        if (fc.getAllVariablesInMethod() != null) b.addAllAllVariablesInMethod(fc.getAllVariablesInMethod());
        if (Boolean.TRUE.equals(fc.getIsGpu())) b.setIsGpu(true);
        if (fc.getOpenClCode() != null) b.setOpenClCode(fc.getOpenClCode());
        if (fc.getGpuParallelismArgIndices() != null) b.addAllGpuParallelismArgIndices(fc.getGpuParallelismArgIndices());
        if (fc.getGpuWorkDimArgIndex() != null) b.setGpuWorkDimArgIndex(fc.getGpuWorkDimArgIndex());
        return b.build();
    }

    private static RuleEngineProto.WhileBlock toProto(While w) {
        RuleEngineProto.WhileBlock.Builder b = RuleEngineProto.WhileBlock.newBuilder();
        if (w.getId() != null) b.setId(w.getId());
        if (w.getCodeStrPtr() != null) b.setCodeStrPtr(w.getCodeStrPtr());
        if (w.getImmediateParentRuleEngineInputUnitId() != null) b.setImmediateParentRuleEngineInputUnitId(w.getImmediateParentRuleEngineInputUnitId());
        if (w.getConditionId() != null) b.setConditionId(w.getConditionId());
        if (w.getWhileCommandId() != null) b.setWhileCommandId(w.getWhileCommandId());
        return b.build();
    }

    private static RuleEngineProto.RedefineArrayCommand toProto(RedefineArrayCommand r) {
        RuleEngineProto.RedefineArrayCommand.Builder b = RuleEngineProto.RedefineArrayCommand.newBuilder();
        if (r.getId() != null) b.setId(r.getId());
        if (r.getCodeStrPtr() != null) b.setCodeStrPtr(r.getCodeStrPtr());
        if (r.getArrayId() != null) b.setArrayId(r.getArrayId());
        if (r.getNewDimensions() != null) b.addAllNewDimensions(r.getNewDimensions());
        return b.build();
    }

    private static RuleEngineProto.ReturnOperation toProto(ReturnOperation r) {
        RuleEngineProto.ReturnOperation.Builder b = RuleEngineProto.ReturnOperation.newBuilder();
        if (r.getId() != null) b.setId(r.getId());
        if (r.getCodeStrPtr() != null) b.setCodeStrPtr(r.getCodeStrPtr());
        if (r.getOperatorType() != null) b.setOperatorType(r.getOperatorType());
        if (r.getOperand1() != null) b.setOperand1(r.getOperand1());
        if (r.getOperand2() != null) b.setOperand2(r.getOperand2());
        return b.build();
    }
}
