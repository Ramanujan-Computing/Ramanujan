#include "../input/ObjectInstance.hpp"
#include "../ruleEngineObject/ObjectInstanceRE.h"

// Caller takes ownership of the returned ObjectInstanceRE and is responsible for deallocation.
RuleEngineInputUnits* ObjectInstance::getInternalAnalogy() {
    return new ObjectInstanceRE(this);
}
