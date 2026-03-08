package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a concrete object instance created from a class definition.
 *
 * <p>Each time {@code obj = ClassName(args)} is compiled, an ObjectInstance is
 * created that binds the instance's logical field names to the unique Variable IDs
 * generated for that specific instance.  The same Variable IDs are passed (by
 * reference) whenever a method is invoked on the object, ensuring that mutations
 * inside the method are reflected in the caller's scope.</p>
 *
 * <h3>Example</h3>
 * <pre>
 * p = Person("Alice", 30)
 * </pre>
 * Produces an ObjectInstance with:
 * <ul>
 *   <li>instanceName = "p"</li>
 *   <li>className    = "Person"</li>
 *   <li>fieldVariableIds = {"name": "&lt;uuid&gt;", "age": "&lt;uuid&gt;"}</li>
 * </ul>
 * The Variables with those IDs are also added to
 * {@link in.ramanujan.pojo.RuleEngineInput#getVariables()}.
 *
 * <h3>Copy-by-reference semantics</h3>
 * <p>Passing {@code p} to a function or method call passes the field Variable IDs
 * stored in this map.  Since the rule engine works with Variable IDs (references),
 * any mutation inside the callee is automatically visible to the caller — the
 * objects are always passed by reference.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ObjectInstance extends RuleEngineInputUnits {

    /** Variable name used in source code, e.g. "p". */
    private String instanceName;

    /** Name of the class this instance belongs to, e.g. "Person". */
    private String className;

    /**
     * Maps each field name to the unique Variable ID that stores its value for
     * this particular instance.
     * Insertion order is preserved (LinkedHashMap) so that the field ordering
     * matches {@link ClassDefinition#getFieldNames()}.
     */
    private Map<String, String> fieldVariableIds = new LinkedHashMap<>();

    public ObjectInstance() {
        setClazz(ObjectInstance.class);
    }
}
