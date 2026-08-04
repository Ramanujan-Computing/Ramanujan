#include "ObjectInstanceStore.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/ArrayRE.h"

// Static member definitions
std::unordered_map<std::string, ObjectInstance*> ObjectInstanceStore::instances;
std::unordered_map<std::string, ClassDefinition*> ObjectInstanceStore::classDefs;

void NewObjectCommandRE::setFields(
    std::unordered_map<std::string, RuleEngineInputUnits*>* map) {
    ClassDefinition* classDef = ObjectInstanceStore::getClass(className);
    if (!classDef) return;

    for (const auto& fn : classDef->scalarFieldNames) {
        std::string varId = "class_" + className + "_" + fn + "_var";
        auto it = map->find(varId);
        if (it != map->end()) {
            VariableRE* varRE = dynamic_cast<VariableRE*>(it->second);
            initialScalarValues.push_back(varRE ? *varRE->getValPtrPtr() : 0.0);
        } else {
            initialScalarValues.push_back(0.0);
        }
    }

    for (const auto& fn : classDef->arrayFieldNames) {
        std::string arrId = "class_" + className + "_" + fn + "_arr";
        auto it = map->find(arrId);
        if (it != map->end()) {
            ArrayRE* arrRE = dynamic_cast<ArrayRE*>(it->second);
            if (arrRE) {
                initialArrayValues.push_back(
                    new ArrayValue(arrRE->arrayValue.arrayValue, false));
            } else {
                initialArrayValues.push_back(nullptr);
            }
        } else {
            initialArrayValues.push_back(nullptr);
        }
    }
}

RuleEngineInputUnits* NewObjectCommandRE::process() {
    ObjectInstance* inst = new ObjectInstance();
    inst->objectHandleId = objectHandleId;
    inst->className = className;
    inst->scalarSlotValues = initialScalarValues;

    for (auto* av : initialArrayValues) {
        if (av) {
            inst->arraySlotValues.push_back(new ArrayValue(av, false));
        } else {
            inst->arraySlotValues.push_back(nullptr);
        }
    }

    ObjectInstanceStore::add(inst);
    return nextUnit;
}
