package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.List;

/**
 * Represents a return statement in a function.
 * 
 * <p>Return objects are used to specify what values should be returned from a function.
 * For tuple unpacking (e.g., a, b = func()), the return values are directly assigned
 * to the target variables passed as additional function arguments.</p>
 * 
 * <h3>Usage</h3>
 * <pre>
 * Python:
 *   def get_coords():
 *       return 10, 20
 *   
 *   a, b = get_coords()
 * 
 * Implementation:
 *   - Function declares return values (10, 20)
 *   - Target variables (a, b) are passed as additional arguments
 *   - Function assigns return values to these target variables
 * </pre>
 * 
 * <h3>Fields</h3>
 * <ul>
 *   <li><b>returnValueIds:</b> List of variable/constant IDs to return</li>
 * </ul>
 * 
 * <h3>TODO</h3>
 * <ul>
 *   <li>Arrays cannot be returned - only scalar values (Variables) are supported</li>
 *   <li>Native deserialization needs to be implemented in ramanujan-native</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Return extends RuleEngineInputUnits {
    
    /**
     * List of variable or constant IDs that represent the values to return.
     * For tuple returns, this contains multiple IDs (e.g., [var_a_id, var_b_id]).
     * For single returns, this contains one ID.
     */
    private List<String> returnValueIds;
    
    public Return() {
        setClazz(Return.class);
    }
}
