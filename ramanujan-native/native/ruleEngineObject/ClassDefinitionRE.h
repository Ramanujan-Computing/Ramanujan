//
// ClassDefinitionRE.h
//

#ifndef NATIVE_CLASSDEFINITIONRE_H
#define NATIVE_CLASSDEFINITIONRE_H

#include "RuleEngineInputUnits.hpp"
#include "FunctionCallRE.h"
#include "../input/ClassDefinition.hpp"
#include <unordered_map>
#include <string>

/**
 * Rule engine representation of a class definition (ClassDefinitionRE).
 *
 * ClassDefinitionRE is the runtime mirror of ClassDefinition.  It holds resolved
 * pointers to the FunctionCallRE objects for the constructor and every method, so
 * that method dispatch can be done by pointer lookup rather than string key lookup.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * OOP EXECUTION MODEL IN THE RULE ENGINE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The Ramanujan rule engine has NO special OOP runtime constructs.  All object-
 * oriented semantics are fully encoded by the *compiler* (middleware) before the
 * JSON payload reaches the native layer.  The interpreter therefore handles OOP
 * transparently through the three mechanisms described below.
 *
 * 1. OBJECT CREATION
 * ──────────────────
 * Python:
 *     p = Person("Alice", 30)
 *
 * What the compiler emits:
 *   • One Variable per field: var_person_name (string→double), var_person_age.
 *   • A FunctionCall for the constructor: id="Person___init__",
 *     arguments=["var_person_name", "var_person_age", arg_alice_id, arg_30_id].
 *     The field Variable IDs are prepended before the user-supplied arguments.
 *
 * What the interpreter sees:
 *   A plain FunctionCommandRE executed against the constructor FunctionCallRE.
 *   No class instantiation is needed at runtime — fields are just Variables.
 *
 * 2. METHOD CALLS
 * ───────────────
 * Python:
 *     p.greet()
 *
 * What the compiler emits (verified in convertMethodCall / convertClassMethod):
 *   A FunctionCall call-site: id="Person_greet",
 *   arguments=["var_person_name", "var_person_age"]  (object's field Variable IDs).
 *   The function definition: id="Person_greet", arguments=[self_name_param, ...],
 *   firstCommandId=<first command>, allVariablesInMethod=[...].
 *   The call-site argument count matches the definition parameter count exactly.
 *
 * What the interpreter sees:
 *   A plain FunctionCommandRE executed against the "Person_greet" FunctionCallRE.
 *   The method's `self` parameters map to the same VariableRE objects as the
 *   caller's field Variables — which is exactly copy-by-reference semantics.
 *
 * 3. OBJECTS AS FUNCTION ARGUMENTS (copy-by-reference)
 * ─────────────────────────────────────────────────────
 * Python:
 *     foo(p)   # where p is a Person instance
 *
 * MECHANISM A — field-expansion (current compiler strategy for method calls):
 *   arguments=["var_person_name", "var_person_age"]
 *   The object is *expanded* into its field Variable IDs at compile time.
 *   Each field ID resolves to the same VariableRE that the calling scope uses.
 *   FunctionCommandRE propagates the final value of each parameter back to the
 *   calling argument via saveRestoreAndPropagate(), so any mutation inside the
 *   callee is visible to the caller after the call returns.
 *
 * MECHANISM B — object-as-single-arg (ObjectDataContainerValue / ObjectInstanceRE):
 *   When the compiler passes an ObjectInstance ID as the argument (future path),
 *   FunctionCommandRE detects that both the called parameter and the calling
 *   argument are ObjectInstanceRE objects (AbstractDataContainer backed by
 *   ObjectDataContainerValue).  ObjectDataContainerValue::saveValueAndCopyFrom
 *   replaces the callee's ObjectInstanceRE* with the caller's, sharing all field
 *   VariableREs.  On return, saveRestoreAndPropagate() restores the callee's
 *   original pointer — no value copying is needed since mutations are already in
 *   the caller's VariableREs (see ObjectDataContainerValue.h for full details).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * ClassDefinitionRE itself does NOT participate in execution (process() is a
 * no-op).  It serves as a pre-resolved metadata table that allows other
 * components to look up the constructor or a method's FunctionCallRE by the
 * method's function ID without repeating map lookups.
 */
class ClassDefinitionRE : public RuleEngineInputUnits {
    ClassDefinition* classDefinition;

    /** Pre-resolved FunctionCallRE for the constructor (__init__), or nullptr. */
    FunctionCallRE* constructorRE = nullptr;

    /**
     * Pre-resolved FunctionCallRE for each method, keyed by the method's
     * qualified function ID (e.g., "Person_greet").
     */
    std::unordered_map<std::string, FunctionCallRE*> methodREMap;

public:
    /**
     * Constructs a ClassDefinitionRE from the corresponding input struct.
     * Pointer resolution is deferred to setFields().
     */
    explicit ClassDefinitionRE(ClassDefinition* classDef) {
        this->classDefinition = classDef;
        this->id = classDef->id;
    }

    /**
     * Resolves all constructor and method FunctionCallRE pointers from the map.
     * Called during Processor::fixGraph() after all units have been registered.
     *
     * @param map  Global ID → RuleEngineInputUnits* map
     */
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>* map) override {
        if (!classDefinition->constructorFunctionId.empty()) {
            auto it = map->find(classDefinition->constructorFunctionId);
            if (it != map->end()) {
                constructorRE = dynamic_cast<FunctionCallRE*>(it->second);
            }
        }
        for (const std::string& methodId : classDefinition->methodFunctionIds) {
            auto it = map->find(methodId);
            if (it != map->end()) {
                FunctionCallRE* re = dynamic_cast<FunctionCallRE*>(it->second);
                if (re != nullptr) {
                    methodREMap[methodId] = re;
                }
            }
        }
    }

    /**
     * Class definitions do not execute — this is a metadata-only node.
     * @return nullptr always
     */
    RuleEngineInputUnits* process() override {
        return nullptr;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    /** @return The unqualified class name, e.g. "Person". */
    const std::string& getClassName() const {
        return classDefinition->className;
    }

    /**
     * @return The pre-resolved constructor FunctionCallRE, or nullptr if
     *         the class has no explicit __init__.
     */
    FunctionCallRE* getConstructorRE() const {
        return constructorRE;
    }

    /**
     * Looks up a non-constructor method's FunctionCallRE by its qualified ID.
     * @param methodFunctionId  e.g. "Person_greet"
     * @return The resolved FunctionCallRE*, or nullptr if not found.
     */
    FunctionCallRE* getMethodRE(const std::string& methodFunctionId) const {
        auto it = methodREMap.find(methodFunctionId);
        return (it != methodREMap.end()) ? it->second : nullptr;
    }

    void destroy() override {}
};

#endif // NATIVE_CLASSDEFINITIONRE_H
