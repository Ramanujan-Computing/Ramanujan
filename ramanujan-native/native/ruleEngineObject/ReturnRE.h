#ifndef NATIVE_RETURNRE_H
#define NATIVE_RETURNRE_H

#include "RuleEngineInputUnits.hpp"
#include "FunctionCommandRE.h"
#include "dataContainer/VariableRE.h"
#include "CommandRE.h"
#include <vector>
#include <string>
#include <utility>

/**
 * ReturnRE represents a return statement in the rule engine.
 * 
 * Handles multiple return value assignments in a single command for efficiency.
 * Instead of chaining N ReturnOperationRE commands, this executes all assignments
 * at once, reducing command execution overhead from N+1 to 1.
 * 
 * Stores pairs of (target, source) operands and performs sequential assignments
 * v1 = v2 for all pairs before propagating the return flag.
 */
class ReturnRE : public RuleEngineInputUnits {
public:
    std::vector<std::string> returnValueIds;
    std::vector<VariableRE*> returnValueREs;
    
    // Pairs of (target_id, source_id) for return value assignments
    std::vector<std::pair<std::string, std::string>> assignmentPairs;
    
    // Fixed-size arrays for efficient execution (max 255 return values)
    int assignmentCount;
    DoublePtr* targetValues[255];
    DoublePtr* sourceValues[255];

    ReturnRE(std::vector<std::string> returnValueIds) 
        : returnValueIds(returnValueIds), assignmentCount(0) {
    }
    
    // Constructor that accepts assignment pairs
    ReturnRE(std::vector<std::string> returnValueIds,
             std::vector<std::pair<std::string, std::string>> assignmentPairs)
        : returnValueIds(returnValueIds), assignmentPairs(assignmentPairs), assignmentCount(0) {
    }

    void destroy() override {
        // No dynamic allocations to clean up (using stack arrays)
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*> *map) override {
        returnValueREs.clear();
        
        // Resolve return value IDs to their corresponding VariableRE objects
        for (const auto& varId : returnValueIds) {
            auto it = map->find(varId);
            if (it != map->end()) {
                VariableRE* varRe = dynamic_cast<VariableRE*>(it->second);
                if (varRe) {
                    returnValueREs.push_back(varRe);
                }
            }
        }
        
        // Resolve assignment pairs to fixed arrays
        assignmentCount = 0;
        
        for (const auto& pair : assignmentPairs) {
            if (assignmentCount >= 255) {
                // Max return values reached
                break;
            }
            
            CommandRE* target = dynamic_cast<CommandRE*>(getFromMap(map, pair.first));
            CommandRE* source = dynamic_cast<CommandRE*>(getFromMap(map, pair.second));
            
            if (target && source) {
                DoublePtr* v1 = target->getVar();
                DoublePtr* v2 = source->getVar();
                
                if (v1 && v2) {
                    targetValues[assignmentCount] = v1;
                    sourceValues[assignmentCount] = v2;
                    assignmentCount++;
                }
            }
        }
    }

    CommandRE* process() override {
        // Execute all return value assignments sequentially
        for (int i = 0; i < assignmentCount; i++) {
            targetValues[i]->value = sourceValues[i]->value;
        }
        
#ifdef DEBUG_BUILD
        debugger->commitDebugPoint();
#endif
        
        // Propagate return flag up to parent scope
        immediateParent->encounteredReturn = true;
        
        // Return statement hit - return nullptr to stop execution
        return nullptr;
    }

    // Placeholder for future return execution logic
    void execute() {
        // TODO: Implement return statement execution if needed
        // Currently handled in process()
    }
};

#endif // NATIVE_RETURNRE_H
