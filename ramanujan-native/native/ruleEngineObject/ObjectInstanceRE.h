//
// ObjectInstanceRE.h
//

#ifndef NATIVE_OBJECTINSTANCERE_H
#define NATIVE_OBJECTINSTANCERE_H

#include "RuleEngineInputUnits.hpp"
#include "dataContainer/VariableRE.h"
#include "dataContainer/AbstractDataContainer.h"
#include "../input/ObjectInstance.hpp"
#include <unordered_map>
#include <string>

// Forward declaration to break circular dependency
// (ObjectDataContainerValue.h includes ObjectInstanceRE.h via this header)
class ObjectDataContainerValue;

/**
 * Rule engine representation of a concrete object instance (ObjectInstanceRE).
 *
 * ObjectInstanceRE is the runtime mirror of ObjectInstance.  It pre-resolves
 * each field name to the live VariableRE* that backs that field for this
 * specific instance, enabling O(1) field lookup by name and providing a
 * single authoritative location for field introspection at debug / trace time.
 *
 * ObjectInstanceRE also implements AbstractDataContainer so that class objects
 * can be passed as single-unit arguments to functions, with FunctionCommandRE
 * handling the by-reference semantics via ObjectDataContainerValue.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * COPY-BY-REFERENCE SEMANTICS
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * TWO mechanisms provide by-reference object passing:
 *
 * 1. FIELD-EXPANSION (current compiler strategy for method/constructor calls):
 *    The compiler expands obj.method() into a FunctionCall whose arguments list
 *    contains each field Variable ID directly.  Inside FunctionCommandRE these
 *    IDs resolve to the same VariableRE objects as the caller's fields, so any
 *    mutation is immediately visible at the call site.
 *
 * 2. OBJECT-AS-SINGLE-ARG (ObjectDataContainerValue mechanism):
 *    When the compiler passes an ObjectInstance ID as a function argument,
 *    FunctionCommandRE detects that the argument resolves to an ObjectInstanceRE
 *    (an AbstractDataContainer backed by ObjectDataContainerValue).
 *    ObjectDataContainerValue::saveValueAndCopyFrom() replaces the callee
 *    parameter's ObjectInstanceRE pointer with the caller's, so both share the
 *    same field VariableREs.  On return, saveRestoreAndPropagate() restores the
 *    original pointer — no value copying is needed since changes are already in
 *    the caller's VariableREs.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
class ObjectInstanceRE : public RuleEngineInputUnits, public AbstractDataContainer {
    ObjectInstance* objectInstance;

    /**
     * Maps each field name to the live VariableRE that holds its value for this
     * instance.  Populated during setFields() after all VariableRE objects have
     * been registered in the global map.
     */
    std::unordered_map<std::string, VariableRE*> fieldREMap;

    /** DataContainerValue backing AbstractDataContainer::valPtr for this object. */
    ObjectDataContainerValue* objectDataContainerValue = nullptr;

public:
    /**
     * Constructs an ObjectInstanceRE from the corresponding input struct.
     * Field pointer resolution is deferred to setFields().
     */
    explicit ObjectInstanceRE(ObjectInstance* instance);

    ~ObjectInstanceRE();

    /**
     * Resolves each field's Variable ID to its live VariableRE* from the map.
     * Called during Processor::fixGraph() after all Variable RE objects have
     * been registered.
     *
     * @param map  Global ID -> RuleEngineInputUnits* map
     */
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>* map) override {
        for (const auto& entry : objectInstance->fieldVariableIds) {
            const std::string& fieldName = entry.first;
            const std::string& varId     = entry.second;
            auto it = map->find(varId);
            if (it != map->end()) {
                VariableRE* re = dynamic_cast<VariableRE*>(it->second);
                if (re != nullptr) {
                    fieldREMap[fieldName] = re;
                }
            }
        }
    }

    /**
     * Object instances do not execute — this is a metadata-only node.
     * @return nullptr always
     */
    RuleEngineInputUnits* process() override {
        return nullptr;
    }

    // Accessors

    /** @return The source-code variable name for this instance, e.g. "p". */
    const std::string& getInstanceName() const {
        return objectInstance->instanceName;
    }

    /** @return The class this instance belongs to, e.g. "Person". */
    const std::string& getClassName() const {
        return objectInstance->className;
    }

    /**
     * Looks up the live VariableRE for a given field by name.
     *
     * @param fieldName  e.g. "name" or "age"
     * @return  The resolved VariableRE*, or nullptr if the field is unknown.
     */
    VariableRE* getFieldRE(const std::string& fieldName) const {
        auto it = fieldREMap.find(fieldName);
        return (it != fieldREMap.end()) ? it->second : nullptr;
    }

    /**
     * @return  The full field-name -> VariableRE* map for this instance.
     *          Useful for iterating all fields (e.g. in a debugger).
     */
    const std::unordered_map<std::string, VariableRE*>& getFieldREMap() const {
        return fieldREMap;
    }

    void destroy() override {}
};

// Include the full definition of ObjectDataContainerValue after ObjectInstanceRE
// is fully declared, so that ObjectDataContainerValue can use ObjectInstanceRE*.
#include "dataContainer/ObjectDataContainerValue.h"

// Inline constructor/destructor implementations

inline ObjectInstanceRE::ObjectInstanceRE(ObjectInstance* instance) {
    this->objectInstance = instance;
    this->id = instance->id;
    // Allocate the ObjectDataContainerValue that backs AbstractDataContainer::valPtr.
    // Caller (Processor/storeInIdMap) owns this ObjectInstanceRE and is responsible
    // for its lifetime.
    objectDataContainerValue = new ObjectDataContainerValue(this);
    valPtr = objectDataContainerValue;
}

inline ObjectInstanceRE::~ObjectInstanceRE() {
    delete objectDataContainerValue;
}

#endif // NATIVE_OBJECTINSTANCERE_H
