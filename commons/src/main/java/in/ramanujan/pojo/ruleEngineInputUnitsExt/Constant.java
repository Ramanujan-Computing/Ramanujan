package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Constant extends RuleEngineInputUnits {
    private Object value;
    private String dataType;

    public Constant() {
        setClazz(Constant.class);
    }

    public void setValueAndDataType(String value) {
        try {
            setValue(Double.parseDouble(value));
            setDataType("Double");
            return;
        } catch (Exception e) {

        }

        try {
            setValue(Integer.parseInt(value));
            setDataType("Integer");
            return;
        } catch (Exception e) {

        }

        try {
            setValue(value);
            setDataType("String");
            return;
        } catch (Exception e) {

        }
    }
}
