package in.ramanujan.translation.codeConverter.pojo;

import java.util.List;

public class VariableAndArrayResult {
    private List<VariableMappingLite> variables;
    private List<ArrayMappingLite> arrays;

    public VariableAndArrayResult(List<VariableMappingLite> variables, List<ArrayMappingLite> arrays) {
        this.variables = variables;
        this.arrays = arrays;
    }

    public List<VariableMappingLite> getVariables() {
        return variables;
    }

    public void setVariables(List<VariableMappingLite> variables) {
        this.variables = variables;
    }

    public List<ArrayMappingLite> getArrays() {
        return arrays;
    }

    public void setArrays(List<ArrayMappingLite> arrays) {
        this.arrays = arrays;
    }
}
