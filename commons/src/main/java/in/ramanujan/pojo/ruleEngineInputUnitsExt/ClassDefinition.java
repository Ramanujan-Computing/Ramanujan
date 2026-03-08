package in.ramanujan.pojo.ruleEngineInputUnitsExt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.pojo.RuleEngineInputUnits;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the definition (blueprint) of a class in the Ramanujan language.
 *
 * <p>ClassDefinition holds the schema for a class: its name, field names, method
 * function IDs, and the constructor function ID.  When an object is instantiated
 * (see {@link ObjectInstance}), per-instance field Variables are created and the
 * constructor function is called with those field Variables passed by reference.</p>
 *
 * <h3>Example</h3>
 * <pre>
 * class Person:
 *     def __init__(self, name, age):
 *         self.name = name
 *         self.age  = age
 *
 *     def greet(self):
 *         return self.name
 * </pre>
 * Produces a ClassDefinition with:
 * <ul>
 *   <li>className = "Person"</li>
 *   <li>fieldNames = ["name", "age"]</li>
 *   <li>constructorFunctionId = "Person___init__"</li>
 *   <li>methodFunctionIds = ["Person_greet"]</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassDefinition extends RuleEngineInputUnits {

    /** Unqualified class name, e.g. "Person". */
    private String className;

    /**
     * Names of instance fields declared via {@code self.field = …} in {@code __init__}.
     * The order here matches the order of the per-field parameters added to every method
     * that takes {@code self}.
     */
    private List<String> fieldNames = new ArrayList<>();

    /**
     * Qualified function IDs for each non-constructor method, in the form
     * {@code "ClassName_methodName"}.  These correspond to {@link FunctionCall#getId()}
     * entries in the {@link in.ramanujan.pojo.RuleEngineInput}.
     */
    private List<String> methodFunctionIds = new ArrayList<>();

    /**
     * Function ID of the constructor, i.e. the {@code __init__} method.
     * Conventionally {@code "ClassName___init__"}.  May be {@code null} when the
     * class has no explicit constructor.
     */
    private String constructorFunctionId;

    public ClassDefinition() {
        setClazz(ClassDefinition.class);
    }
}
