package in.ramanujan.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleEngineInputUnits {
    private String id;
    private Class clazz;
    private Integer codeStrPtr;
}
