#ifndef OBJECT_INSTANCE_STORE_H
#define OBJECT_INSTANCE_STORE_H

#include <string>
#include <unordered_map>
#include <vector>
#include "RuleEngineInputUnits.hpp"
#include "../input/ClassDefinition.hpp"
#include "dataContainer/array/ArrayValue.h"

// Forward declarations to avoid circular includes
class VariableRE;
class ArrayRE;

struct ObjectInstance {
    std::string objectHandleId;
    std::string className;
    std::vector<double> scalarSlotValues;
    std::vector<ArrayValue*> arraySlotValues;

    ~ObjectInstance() {
        for (auto* av : arraySlotValues) {
            if (av) { av->destroy(); delete av; }
        }
    }
};

class ObjectInstanceStore {
public:
    static std::unordered_map<std::string, ObjectInstance*> instances;
    static std::unordered_map<std::string, ClassDefinition*> classDefs;

    static void add(ObjectInstance* inst) {
        instances[inst->objectHandleId] = inst;
    }

    static ObjectInstance* get(const std::string& handleId) {
        auto it = instances.find(handleId);
        return it != instances.end() ? it->second : nullptr;
    }

    static void remove(const std::string& handleId) {
        auto it = instances.find(handleId);
        if (it != instances.end()) {
            delete it->second;
            instances.erase(it);
        }
    }

    static void clear() {
        for (auto& kv : instances) delete kv.second;
        instances.clear();
    }

    static void registerClass(ClassDefinition* cd) {
        classDefs[cd->className] = cd;
    }

    static ClassDefinition* getClass(const std::string& className) {
        auto it = classDefs.find(className);
        return it != classDefs.end() ? it->second : nullptr;
    }

    static void clearClasses() {
        classDefs.clear();
    }
};

class NewObjectCommandRE : public RuleEngineInputUnits {
    std::string className;
    std::string objectHandleId;
    std::vector<double> initialScalarValues;
    std::vector<ArrayValue*> initialArrayValues;
public:
    NewObjectCommandRE(const std::string& cls, const std::string& handle)
        : className(cls), objectHandleId(handle) {}

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>* map) override;
    RuleEngineInputUnits* process() override;
    void destroy() override {}
};

class DeleteObjectCommandRE : public RuleEngineInputUnits {
    std::string objectHandleId;
public:
    explicit DeleteObjectCommandRE(const std::string& handle) : objectHandleId(handle) {}

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>*) override {}

    RuleEngineInputUnits* process() override {
        ObjectInstanceStore::remove(objectHandleId);
        return nextUnit;
    }

    void destroy() override {}
};

#endif
