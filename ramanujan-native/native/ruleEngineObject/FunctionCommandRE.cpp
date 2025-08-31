//
// Created by pranav on 28/3/24.
//

#include "FunctionCommandRE.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/array/ArrayValue.h"
#include "DebugPoint.h"
#include <list>

#include <limits>
#include <random>

/*
         * PARAMETER PASSING: Copy argument value to function parameter (CALL-BY-REFERENCE)
         * 
         * Example: increment_recursive(level 2) call
         * - methodCallingOriginalPlaceHolderAddrs[0] contains value 7 (from level 1)
         * - methodCallingOriginalPlaceHolderAddrs[1] contains value 1 (from level 1's tmpVar)
         * - methodCalledOriginalPlaceHolderAddrs[0] is level 2's parameter n
         * - methodCalledOriginalPlaceHolderAddrs[1] is level 2's parameter depth
         * - After this: level 2's n = 7, depth = 1
         * 
         * CALL-BY-REFERENCE: Any modifications to n or depth in level 2 will be 
         * visible to level 1 when control returns, because they point to the same memory.
         */
FunctionCommandRE::FunctionCommandRE(FunctionCall* functionCommand, FunctionCallRE* functionInfo) {
    this->functionCommandInfo = functionCommand;
    this->functionInfoRE = functionInfo;
}

/*
 * How will we process it?
 * 1. functionInfo has the information about the function. It has the arguments and the first command.
 * 2. it has the allVariablesInMethod.
 * 3. when the function has to be started, we would have to put the values to the arg variables only, and other as 0.0
 * 4. when the function is popped, we would have to transfer the value to calling method variables, and the de-set the args in the current function.
 *    This step is to be done with care, as in the case of recursive function, the value of the variable should not be lost.
 */

void FunctionCommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    functionInfoRE->setFields(map);

    argSize = functionCommandInfo->argumentsSize;

    firstCommand = functionInfoRE->commmandRe;
    if (firstCommand == nullptr) {
        firstCommand = dynamic_cast<CommandRE *>(getFromMap(map, functionInfoRE->functionCall->firstCommandId));
    }


    /*
     * Set up parameter mapping using unified DataContainerValue approach.
     * Use the valPtrPtr mechanism as designed for function parameter passing.
     */

    std::list<DataContainerValue*> methodCalledOriginalPlaceHolderAddrsList;
    std::list<DataContainerValue*> methodCallingOriginalPlaceHolderAddrsList;

    for(int i = 0; i < functionInfoRE->argSize; i++) {
        AbstractDataContainer* calledArg = dynamic_cast<AbstractDataContainer*>(functionInfoRE->arguments[i]);
        AbstractDataContainer* callingArg = dynamic_cast<AbstractDataContainer*>(map->at(functionCommandInfo->arguments[i]));
        
        methodCalledOriginalPlaceHolderAddrsList.push_back(calledArg->valPtr);
        methodCallingOriginalPlaceHolderAddrsList.push_back(callingArg->valPtr);
        
        // Build name mapping for debugging (works for both variables and arrays)
        if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
            arrCount++;
            dataContainerNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            varCount++;
        }
    }

    methodCallingOriginalPlaceHolderAddrs = new DataContainerValue*[argSize];
    methodCalledOriginalPlaceHolderAddrs = new DataContainerValue*[argSize];

    for(int i = 0; i < argSize; i++) {
        methodCalledOriginalPlaceHolderAddrs[i] = methodCalledOriginalPlaceHolderAddrsList.front();
        methodCalledOriginalPlaceHolderAddrsList.pop_front();

        methodCallingOriginalPlaceHolderAddrs[i] = methodCallingOriginalPlaceHolderAddrsList.front();
        methodCallingOriginalPlaceHolderAddrsList.pop_front();
    }

    std::list<DataContainerValue*> methodArgDataContainerAddrList;

    for(int i = 0; i < functionInfoRE->functionCall->allVariablesInMethodSize; i++) {
        AbstractDataContainer* dataContainer = dynamic_cast<AbstractDataContainer*>(functionInfoRE->allVariablesInMethod[i]);
        methodArgDataContainerAddrList.push_back(dataContainer->valPtr);
        
        if(dynamic_cast<ArrayRE*>(functionInfoRE->allVariablesInMethod[i]) != nullptr) {
            totalArrCount++;
        } else {
            totalVarCount++;
        }
    }

    int totalDataContainerCount = totalVarCount + totalArrCount;
    methodArgDataContainerAddr = new DataContainerValue*[totalDataContainerCount];
    methodArgDataContainerCurrentVal = new DataContainerValue*[totalDataContainerCount];

    for(int i = 0; i < totalDataContainerCount; i++) {
        methodArgDataContainerAddr[i] = methodArgDataContainerAddrList.front();
        methodArgDataContainerAddrList.pop_front();
        
        // Create appropriate DataContainerValue copy for saving current state
        if(dynamic_cast<DoublePtr*>(methodArgDataContainerAddr[i]) != nullptr) {
            methodArgDataContainerCurrentVal[i] = new DoublePtr();
        } else if(dynamic_cast<ArrayDataContainerValue*>(methodArgDataContainerAddr[i]) != nullptr) {
            methodArgDataContainerCurrentVal[i] = new ArrayDataContainerValue();
        }
    }
    
    dataContainerStackCurrent = new DataContainerValue*[argSize];
    for(int i = 0; i < argSize; i++) {
        // Create appropriate DataContainerValue copy for stack management
        if(dynamic_cast<DoublePtr*>(methodCalledOriginalPlaceHolderAddrs[i]) != nullptr) {
            dataContainerStackCurrent[i] = new DoublePtr();
        } else if(dynamic_cast<ArrayDataContainerValue*>(methodCalledOriginalPlaceHolderAddrs[i]) != nullptr) {
            dataContainerStackCurrent[i] = new ArrayDataContainerValue();
        }
    }
}

void FunctionCommandRE::process() {
    /*
     * ==================== EXHAUSTIVE EXAMPLE: RECURSIVE FUNCTION EXECUTION ====================
     * 
     * Let's trace through how this process() method would execute a recursive function similar to:
     * 
     * def increment_recursive(n, depth):
     *     if depth <= 0:
     *         return  # Base case - modify n in place
     *     else:
     *         n = n + 1  # Increment n (call-by-reference)
     *         tmpVar = depth - 1  # Operations must be separate assignments
     *         increment_recursive(n, tmpVar)  # Recursive call with variables only
     * 
     * # Usage (standalone function calls):
     * x = 5
     * increment_recursive(x, 3)  # x becomes 8 after execution
     * 
     * CALL STACK VISUALIZATION:
     * 
     * 1. increment_recursive(x=5, depth=3) called:
     *    - Parameters: n = 5, depth = 3
     *    - Local variables: tmpVar
     *    - Executes: n = n + 1 (n becomes 6), tmpVar = depth - 1 (tmpVar becomes 2), then calls increment_recursive(n, tmpVar)
     * 
     * 2. increment_recursive(n=6, depth=2) called (RECURSIVE):
     *    - Parameters: n = 6, depth = 2  
     *    - Local variables: tmpVar
     *    - Executes: n = n + 1 (n becomes 7), tmpVar = depth - 1 (tmpVar becomes 1), then calls increment_recursive(n, tmpVar)
     * 
     * 3. increment_recursive(n=7, depth=1) called (RECURSIVE):
     *    - Parameters: n = 7, depth = 1
     *    - Local variables: tmpVar
     *    - Executes: n = n + 1 (n becomes 8), tmpVar = depth - 1 (tmpVar becomes 0), then calls increment_recursive(n, tmpVar)
     * 
     * 4. increment_recursive(n=8, depth=0) called (RECURSIVE):
     *    - Parameters: n = 8, depth = 0
     *    - Local variables: tmpVar
     *    - Executes: if depth <= 0, return (base case reached)
     * 
     * IMPORTANT: NO RETURN VALUES - ALL MODIFICATION IS CALL-BY-REFERENCE
     * Each function modifies its parameters directly, and these changes are visible
     * to the calling function because arguments are passed by reference.
     * 
     * MEMORY LAYOUT DURING EXECUTION:
     * 
     * methodCalledOriginalPlaceHolderAddrs[0] -> points to function parameter 'n'
     * methodCalledOriginalPlaceHolderAddrs[1] -> points to function parameter 'depth'
     * methodCallingOriginalPlaceHolderAddrs[0] -> points to calling argument 'n'
     * methodCallingOriginalPlaceHolderAddrs[1] -> points to calling argument 'depth'
     * methodArgDataContainerAddr[0] -> parameter 'n' 
     * methodArgDataContainerAddr[1] -> parameter 'depth'
     * methodArgDataContainerAddr[2] -> local variable 'tmpVar'
     * 
     * CRITICAL STACK MANAGEMENT:
     * Each recursive call creates its own process() execution with separate:
     * - dataContainerStackCurrent[] arrays for parameter backup
     * - methodArgDataContainerCurrentVal[] arrays for local variable backup
     * - Proper restoration order to prevent data corruption
     */

    // ==================== DEBUG SETUP ====================
#ifdef DEBUG_BUILD
    // Get debug point for tracking function call execution
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif

    // ==================== PHASE 1: PARAMETER SETUP AND DATA CONTAINER CONTEXT SAVING ====================
    
    /*
     * RECURSIVE EXAMPLE - PHASE 1 DETAILS:
     * 
     * For increment_recursive(x=6, depth=2) -> increment_recursive(n=6, depth=1) call:
     * 
     * BEFORE PHASE 1:
     * - increment_recursive(level 1)'s parameter n = 6, depth = 2
     * - About to call increment_recursive(level 2), so arguments are n=7, tmpVar=1
     * 
     * DURING PHASE 1:
     * 1. dataContainerStackCurrent[0] saves current value of increment_recursive(level 2)'s parameter n (could be garbage)
     * 2. dataContainerStackCurrent[1] saves current value of increment_recursive(level 2)'s parameter depth (could be garbage)
     * 3. increment_recursive(level 2)'s parameter n gets copied value 7 from calling argument
     * 4. increment_recursive(level 2)'s parameter depth gets copied value 1 from calling argument (tmpVar)
     * 
     * AFTER PHASE 1:
     * - increment_recursive(level 2)'s parameters: n = 7, depth = 1 (ready for execution)
     * - increment_recursive(level 1)'s context is preserved in calling arrays
     * 
     * MEMORY STATE:
     * methodCalledOriginalPlaceHolderAddrs[0] -> increment_recursive(level 2)'s parameter n (now = 7)
     * methodCalledOriginalPlaceHolderAddrs[1] -> increment_recursive(level 2)'s parameter depth (now = 1)
     * methodCallingOriginalPlaceHolderAddrs[0] -> increment_recursive(level 1)'s argument value (7)
     * methodCallingOriginalPlaceHolderAddrs[1] -> increment_recursive(level 1)'s argument value (1)
     * dataContainerStackCurrent[0] -> backup of increment_recursive(level 2)'s original n value
     * dataContainerStackCurrent[1] -> backup of increment_recursive(level 2)'s original depth value
     */
    
    /*
     * For each parameter passed to the function:
     * 1. Save the original DataContainerValue content that the parameter contains (for restoration)
     * 2. Copy the calling argument's DataContainerValue content to the called function's parameter
     * 3. Save the current state of all data containers for later restoration
     */
    for (int i = 0; i < argSize; i++) {
#ifdef DEBUG_BUILD
        // Record the current function data container value for debugging
        if (i < varCount) {
            DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(methodCallingOriginalPlaceHolderAddrs[i]);
            if (doublePtr) {
                debugPoint->addCurrentFuncVal(*(doublePtr->value));
            }
        }
#endif
        /*
         * RECURSIVE SAFETY: Save the called function's parameter current value
         * 
         * Example: increment_recursive(level 2) call
         * - dataContainerStackCurrent[0] saves whatever value n had before (garbage)
         * - dataContainerStackCurrent[1] saves whatever value depth had before (garbage)
         * - This is crucial for nested calls: level 1 -> level 2 -> level 3 -> level 4
         * - Each level must restore its parameter state when unwinding
         */
        dataContainerStackCurrent[i]->copyDataContainerValue(methodCalledOriginalPlaceHolderAddrs[i]);
        
        /*
         * PARAMETER PASSING: Copy argument value to function parameter (CALL-BY-REFERENCE)
         * 
         * Example: increment_recursive(level 2) call
         * - methodCallingOriginalPlaceHolderAddrs[0] contains value 7 (from level 1)
         * - methodCallingOriginalPlaceHolderAddrs[1] contains value 1 (from level 1)
         * - methodCalledOriginalPlaceHolderAddrs[0] is level 2's parameter n
         * - methodCalledOriginalPlaceHolderAddrs[1] is level 2's parameter depth
         * - After this: level 2's n = 7, depth = 1
         * 
         * CALL-BY-REFERENCE: Any modifications to n or depth in level 2 will be 
         * visible to level 1 when control returns, because they point to the same memory.
         */
        methodCalledOriginalPlaceHolderAddrs[i]->copyDataContainerValue(methodCallingOriginalPlaceHolderAddrs[i]);
    }

#ifdef DEBUG_BUILD
    // Record data container name mappings for debugging purposes
    // This helps track which calling data containers correspond to which function parameters
    for(auto it = dataContainerNameMethodMap.begin(); it != dataContainerNameMethodMap.end(); it++) {
        debugPoint->addArrayInFuncCall(it->first, it->second);
    }
    debugger->commitDebugPoint();
#endif

    /*
     * LOCAL VARIABLE BACKUP:
     * 
     * For all data containers in the function (both parameters and locals):
     * Save their current DataContainerValue content so they can be restored after function execution.
     * 
     * RECURSIVE EXAMPLE:
     * increment_recursive(level 2) has:
     * - methodArgDataContainerAddr[0] = parameter 'n' (now = 7)
     * - methodArgDataContainerAddr[1] = parameter 'depth' (now = 1)
     * - methodArgDataContainerAddr[2] = local variable 'tmpVar' (could be garbage)
     * 
     * We save all values in methodArgDataContainerCurrentVal[0], [1], and [2]
     * so when level 2 returns, level 1 can continue with clean state
     */
    int totalDataContainerCount = totalVarCount + totalArrCount;
    for(int i = 0; i < totalDataContainerCount; i++) {
        methodArgDataContainerCurrentVal[i]->copyDataContainerValue(methodArgDataContainerAddr[i]);
    }

    // ==================== PHASE 2: FUNCTION BODY EXECUTION ====================
    
    /*
     * RECURSIVE EXECUTION DETAILS (STANDALONE FUNCTION CALLS):
     * 
     * increment_recursive(level 2) execution:
     * 1. Checks: if depth <= 0 (false, since depth = 1)
     * 2. Executes: n = n + 1 (n becomes 8)
     * 3. Executes: tmpVar = depth - 1 (tmpVar becomes 0)
     * 4. Prepares arguments for next call: n=8, tmpVar=0
     * 5. Executes: increment_recursive(n, tmpVar) as STANDALONE FUNCTION CALL
     * 6. This creates ANOTHER FunctionCommandRE::process() call for level 3
     * 7. level 3 goes through its own complete PHASE 1-4 cycle
     * 8. level 3 hits base case (depth=0) and returns without modification
     * 9. level 2 continues after the function call (no return value to handle)
     * 10. level 2 execution completes
     * 
     * CRITICAL CALL-BY-REFERENCE SEMANTICS:
     * - When level 2 modifies n from 7 to 8, this change is visible to level 1
     * - When level 3 executes with n=8, depth=0, it hits base case and doesn't modify n
     * - So level 1 sees n=8 when level 2 returns
     * 
     * STANDALONE FUNCTION CALL CONSTRAINT:
     * - increment_recursive() cannot be part of an expression like: result = increment_recursive(x, y)
     * - It must be called as a standalone statement: increment_recursive(x, y);
     * - Operations cannot be directly used as function arguments: increment_recursive(n, depth-1) is INVALID
     * - Operations must be computed first: tmpVar = depth - 1; increment_recursive(n, tmpVar); is VALID
     * - No return value is expected or processed
     * 
     * CRITICAL: Each recursive call has its own process() stack frame with:
     * - Its own dataContainerStackCurrent array
     * - Its own methodArgDataContainerCurrentVal backup
     * - Complete isolation from other call levels
     */
    
    /*
     * Execute the function body by traversing the command chain.
     * Each command returns the next command to execute, forming a linked execution chain.
     * Execution continues until there are no more commands (nullptr).
     */
    CommandRE* command = firstCommand;
    while(command != nullptr) {
        /*
         * RECURSIVE CALL HAPPENS HERE (STANDALONE):
         * When command->get() encounters a function call like increment_recursive(n, tmpVar),
         * it creates a NEW FunctionCommandRE object and calls ITS process() method.
         * This creates a completely separate execution context while preserving
         * the current one in our local variables.
         * 
         * IMPORTANT: The function call is executed as a standalone statement.
         * No return value is expected or processed. All communication happens
         * through call-by-reference parameter modification.
         * 
         * ARGUMENT CONSTRAINT: Only variables/arrays can be passed as arguments.
         * Operations like (depth-1) must be computed in separate assignment statements first.
         */
        command = command->get();  // Execute current command and get next command
    }

    // ==================== PHASE 3: NON-PARAMETER DATA CONTAINER RESTORATION ====================
    
    /*
     * RECURSIVE CLEANUP - PHASE 3:
     * 
     * After increment_recursive(level 2) finishes execution:
     * 
     * BEFORE PHASE 3:
     * - level 2's parameter n = 8 (modified during execution)
     * - level 2's parameter depth = 1 (unchanged)
     * - level 2's local tmpVar = 0 (used for function argument)
     * 
     * DURING PHASE 3:
     * - We restore ONLY local variables (tmpVar) to their pre-execution state
     * - We DON'T restore parameters yet - they contain modified values for call-by-reference!
     * 
     * WHY SKIP PARAMETERS?
     * The parameters contain the modified values that need to be visible to the calling function.
     * In call-by-reference semantics, parameter modifications are the primary communication mechanism.
     * We restore them last to ensure proper data flow.
     */
    
    /*
     * First, restore all non-parameter data containers to their pre-function-execution state.
     * This ensures proper cleanup and prevents interference between recursive calls.
     * 
     * IMPORTANT: We only restore data containers that are NOT function parameters,
     * because parameters need to retain their modified values for call-by-reference semantics.
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        /*
         * RECURSIVE ISOLATION EXAMPLE:
         * 
         * increment_recursive(level 2) local variables:
         * - methodArgDataContainerAddr[2] = tmpVar (currently = 0, used for function argument)
         * - methodArgDataContainerCurrentVal[2] = saved pre-execution value of tmpVar (garbage)
         * 
         * After restoration:
         * - tmpVar is reset to its pre-execution state
         * - This prevents level 2's locals from corrupting level 1's execution
         * - But parameters n and depth retain their modified values (8 and 1)
         * 
         * Restore non-parameter data container to its pre-function-execution value
         * 
         * FIELD EXPLANATION:
         * - methodArgDataContainerAddr[i] points to the data container
         * - methodArgDataContainerCurrentVal[i] contains the saved value from Phase 1
         * - We start from argSize to skip parameter data containers (indices 0 to argSize-1)
         */
        methodArgDataContainerAddr[i]->copyDataContainerValue(methodArgDataContainerCurrentVal[i]);
    }

    // ==================== PHASE 4: PARAMETER RESTORATION AND CLEANUP ====================
    
    /*
     * RECURSIVE UNWINDING - PHASE 4:
     * 
     * This is the CRITICAL phase for recursive functions with call-by-reference:
     * 
     * increment_recursive(level 2) returning to increment_recursive(level 1):
     * 
     * BEFORE PHASE 4:
     * - level 2's parameter n = 8 (modified during execution)
     * - level 2's parameter depth = 1 (unchanged)
     * - level 1 expects to see these modified values through call-by-reference
     * 
     * DURING PHASE 4:
     * - We restore level 2's parameters to their original state (before Phase 1)
     * - But the calling function (level 1) has already received the modified values
     * - This "rewinds" level 2's state as if the call never happened
     * 
     * CALL-BY-REFERENCE MECHANISM:
     * - level 1's arguments and level 2's parameters point to the same memory
     * - When level 2 modifies its parameters, level 1 sees the changes immediately
     * - Restoring level 2's parameters doesn't affect level 1's view of the data
     * 
     * AFTER PHASE 4:
     * - level 2's parameter state is restored (clean slate)
     * - level 1 continues execution with modified argument values (n=8)
     * - Stack frame is completely clean for next operation
     */
    
    /*
     * CRITICAL: We must restore the calling context regardless of function execution outcome.
     * This is essential for recursive functions and proper stack management.
     * 
     * Restore the original DataContainerValue content that was overwritten during parameter setup.
     * This happens AFTER non-parameter restoration so that call-by-reference semantics are preserved.
     */
    for(int i = 0; i < argSize; i++) {
        /*
         * RECURSIVE STACK UNWINDING WITH CALL-BY-REFERENCE:
         * 
         * Example: increment_recursive(level 3) returning to increment_recursive(level 2)
         * - level 3's parameter n was set to 8 in Phase 1
         * - level 3 hit base case and didn't modify n (still 8)
         * - dataContainerStackCurrent[0] contains the original value n had before Phase 1 (garbage)
         * - We restore n to that original value, effectively "undoing" the parameter setup
         * - level 2 sees that its argument n is still 8 (the call-by-reference effect)
         * 
         * This ensures each recursive level is properly isolated and restored,
         * while preserving call-by-reference semantics for parameter modifications.
         * 
         * Restore the original DataContainerValue content that the parameter contained
         */
        methodCalledOriginalPlaceHolderAddrs[i]->copyDataContainerValue(dataContainerStackCurrent[i]);
    }

    /*
     * MEMORY CLEANUP:
     * 
     * Clean up the stack management memory allocated for this function call.
     * Each recursive call allocates its own dataContainerStackCurrent array,
     * so each must clean up its own allocation.
     * 
     * RECURSIVE SAFETY:
     * This cleanup only affects the current call level.
     * Other recursive levels have their own separate allocations.
     */
    for(int i = 0; i < argSize; i++) {
        delete dataContainerStackCurrent[i];
    }
    delete[] dataContainerStackCurrent;

    /*
     * RECURSIVE EXECUTION COMPLETE:
     * 
     * At this point, the current function call is completely unwound:
     * 1. All parameters are restored to pre-call state
     * 2. All local variables are restored to pre-execution state  
     * 3. All temporary memory is cleaned up
     * 4. Control returns to the calling function (or main)
     * 
     * For our increment_recursive(x=5, depth=3) example:
     * - increment_recursive(level 4) hits base case, returns to level 3
     * - increment_recursive(level 3) incremented n from 7 to 8, returns to level 2  
     * - increment_recursive(level 2) incremented n from 6 to 7, returns to level 1
     * - increment_recursive(level 1) incremented n from 5 to 6, returns to main
     * - Final result: x = 8 (original 5 + 3 increments through recursion)
     * 
     * Each level maintains perfect isolation through this process while preserving
     * call-by-reference semantics for parameter modifications.
     */

    // Function execution complete - calling context fully restored
}

void BuiltInFunctionsImpl::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    std::list<DataContainerValue*> methodArgDataContainerAddrList;

    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        AbstractDataContainer* arg = dynamic_cast<AbstractDataContainer*>(map->at(functionCommandInfo->arguments[i]));
        methodArgDataContainerAddrList.push_back(arg->valPtr);
        
        if (dynamic_cast<ArrayRE *>(map->at(functionCommandInfo->arguments[i])) != nullptr) {
            arrCount++;
        } else {
            varCount++;
        }
    }

    methodArgDataContainerAddr = new DataContainerValue*[functionCommandInfo->argumentsSize];

    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        methodArgDataContainerAddr[i] = methodArgDataContainerAddrList.front();
        methodArgDataContainerAddrList.pop_front();
    }
}

void NINF::process() {
    if(arrCount > 0) {
        // This is an array (ArrayDataContainerValue)
        ArrayDataContainerValue* arrayPtr = static_cast<ArrayDataContainerValue*>(methodArgDataContainerAddr[0]);
        ArrayValue* arrayValue = arrayPtr->arrayValue;
        for(int j = 0; j < arrayValue->totalSize; j++) {
            arrayValue->val[j] = -std::numeric_limits<double>::infinity();
        }
    } else {
        // This is a variable (DoublePtr)
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = -std::numeric_limits<double>::infinity();
    }
}

void PINF::process() {
    if(arrCount > 0) {
        // This is an array (ArrayDataContainerValue)
        ArrayDataContainerValue* arrayPtr = static_cast<ArrayDataContainerValue*>(methodArgDataContainerAddr[0]);
        ArrayValue* arrayValue = arrayPtr->arrayValue;
        for(int j = 0; j < arrayValue->totalSize; j++) {
            arrayValue->val[j] = std::numeric_limits<double>::infinity();
        }
    } else {
        // This is a variable (DoublePtr)
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::numeric_limits<double>::infinity();
    }
}

static std::random_device rd;  // Non-deterministic random seed
static std::mt19937 gen(rd()); // Mersenne Twister engine
static std::uniform_real_distribution<> dis(0.0, 1.0);

void RAND::process() {
    if(arrCount > 0) {
        // This is an array (ArrayDataContainerValue)
        ArrayDataContainerValue* arrayPtr = static_cast<ArrayDataContainerValue*>(methodArgDataContainerAddr[0]);
        ArrayValue* arrayValue = arrayPtr->arrayValue;
        for(int j = 0; j < arrayValue->totalSize; j++) {
            arrayValue->val[j] = dis(gen);
        }
    } else {
        // This is a variable (DoublePtr)
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = dis(gen);
    }
}

// All variable based built-in methods:
void ABS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::abs(*(doublePtr->value));
    }
}

void SIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::sin(*(doublePtr->value));
    }
}

void COS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::cos(*(doublePtr->value));
    }
}

void TAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::tan(*(doublePtr->value));
    }
}

void ASIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::asin(*(doublePtr->value));
    }
}

void ACOS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::acos(*(doublePtr->value));
    }
}

void ATAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::atan(*(doublePtr->value));
    }
}

void FLOOR::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::floor(*(doublePtr->value));
    }
}

void CEIL::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::ceil(*(doublePtr->value));
    }
}

void EXP::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::exp(*(doublePtr->value));
    }
}

void SQRT::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::sqrt(*(doublePtr->value));
    }
}

void POW::process() {
    if(functionCommandInfo->argumentsSize >= 2) {
        DoublePtr* doublePtr1 = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        DoublePtr* doublePtr2 = static_cast<DoublePtr*>(methodArgDataContainerAddr[1]);
        *(doublePtr1->value) = std::pow(*(doublePtr1->value), *(doublePtr2->value));
    }
}