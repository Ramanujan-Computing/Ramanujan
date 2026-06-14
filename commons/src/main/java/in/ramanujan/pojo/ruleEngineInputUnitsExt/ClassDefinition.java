package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassDefinition extends RuleEngineInputUnits {
    private String className;
    private List<String> scalarFieldNames;
    private List<String> arrayFieldNames;

    public ClassDefinition() {
        setClazz(ClassDefinition.class);
    }
}
