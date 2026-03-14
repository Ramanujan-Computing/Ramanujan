#include "../input/ClassDefinition.hpp"
#include "../ruleEngineObject/ClassDefinitionRE.h"

// Caller takes ownership of the returned ClassDefinitionRE and is responsible for deallocation.
RuleEngineInputUnits* ClassDefinition::getInternalAnalogy() {
    return new ClassDefinitionRE(this);
}
