#ifndef NATIVE_OBJECTHANDLEARGRE_H
#define NATIVE_OBJECTHANDLEARGRE_H

#include <string>
#include <vector>
#include "../RuleEngineInputUnits.hpp"

/**
 * Runtime holder for an object-typed function parameter.
 *
 * When a function is called with an object argument, FunctionCommandRE sets
 * currentObjectHandleId to the caller's objectHandleId. The value is saved and
 * restored across nested calls, giving each invocation frame its own binding.
 *
 * ClassBasedFunctionCommandRE checks whether its objectHandleId resolves to an
 * ObjectHandleArgRE at setFields() time, then dereferences it at process() time
 * to get the actual UUID for ObjectInstanceStore lookup.
 */
class ObjectHandleArgRE : public RuleEngineInputUnits {
    std::string currentObjectHandleId;
    std::string className;

public:
    explicit ObjectHandleArgRE(const std::string& cls) : className(cls) {}

    const std::string& get() const { return currentObjectHandleId; }
    void set(const std::string& id) { currentObjectHandleId = id; }
    const std::string& getClassName() const { return className; }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*>*) override {}
    RuleEngineInputUnits* process() override { return nullptr; }

private:
    void destroy() override {}
};

#endif // NATIVE_OBJECTHANDLEARGRE_H
