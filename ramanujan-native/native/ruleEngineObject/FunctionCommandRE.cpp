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

/**
 * Constructor for FunctionCommandRE.
 * Initializes a function call execution context by setting up the relationship
 * between the function call information (caller side) and function definition (callee side).
 * 
 * @param functionCommand Information about the function call being made (caller context)
 * @param functionInfo Rule engine representation of the function definition (callee context)
 */
FunctionCommandRE::FunctionCommandRE(FunctionCall* functionCommand, FunctionCallRE* functionInfo) {
    this->functionCommandInfo = functionCommand;
    this->functionInfoRE = functionInfo;
}

/**
 * FUNCTION EXECUTION OVERVIEW:
 * 
 * The function execution process follows these key steps:
 * 1. functionInfoRE contains the function definition with parameters and execution commands
 * 2. functionCommandInfo contains the calling context with argument values
 * 3. During function start: Parameter variables receive argument values, local variables are initialized
 * 4. During function completion: Final parameter values are propagated back to calling context,
 *    and all function variables are restored to their pre-call state
 * 
 * CRITICAL CONSIDERATION FOR RECURSIVE FUNCTIONS:
 * The restoration process must carefully preserve variable states to handle recursive calls correctly.
 * Each recursive call creates its own variable scope that must be properly isolated and restored.
 * 
 * CALL-BY-REFERENCE SEMANTICS:
 * This system uses call-by-reference semantics where parameters are modified in-place.
 * There are no explicit return statements - instead, parameter modifications are propagated
 * back to the calling context as the mechanism for returning computed results.
 */

/**
 * Sets up field mappings and initializes data structures for function execution.
 * This method performs comprehensive parameter mapping between calling and called functions,
 * including separation of variables and arrays, address mapping setup, and local storage initialization.
 * 
 * @param map Global map containing all rule engine objects indexed by their IDs
 */
void FunctionCommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    // Initialize the function definition's fields first
    functionInfoRE->setFields(map);

    // Set the total number of arguments for this function call
    argSize = functionCommandInfo->argumentsSize;

    // Determine the first command to execute in the function body
    firstCommand = functionInfoRE->commmandRe;
    if (firstCommand == nullptr) {
        firstCommand = dynamic_cast<CommandRE *>(getFromMap(map, functionInfoRE->functionCall->firstCommandId));
    }

    /**
     * PARAMETER MAPPING PHASE:
     * Categorize function parameters into variables and arrays, and establish
     * address mappings between calling arguments and function parameters.
     */

    std::list<DataContainerValue*> methodCalledOriginalPlaceHolderAddrsList;
    std::list<DataContainerValue*> methodCallingOriginalPlaceHolderAddrsList;

    /**
     * ARGUMENT CATEGORIZATION LOOP:
     * Iterate through all function parameters to separate variables from arrays
     * and establish bidirectional address mappings between caller and callee contexts.
     */
    for(int i = 0; i < functionInfoRE->argSize; i++) {
        AbstractDataContainer* calledArg = dynamic_cast<AbstractDataContainer*>(functionInfoRE->arguments[i]);
        AbstractDataContainer* callingArg = dynamic_cast<AbstractDataContainer*>(map->at(functionCommandInfo->arguments[i]));
        
        methodCalledOriginalPlaceHolderAddrsList.push_back(calledArg->valPtr);
        methodCallingOriginalPlaceHolderAddrsList.push_back(callingArg->valPtr);
        
        // Build name mapping for debugging (works for both variables and arrays)
        if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
            // Array parameter found
            arrCount++;
            // Build name mapping for debugging purposes
            dataContainerNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            // Variable parameter found
            varCount++;
        }
    }

    /**
     * MEMORY ALLOCATION FOR PARAMETER MAPPING ARRAYS:
     * Allocate arrays to store address mappings for efficient parameter passing.
     */
    methodCallingOriginalPlaceHolderAddrs = new DataContainerValue*[argSize];
    methodCalledOriginalPlaceHolderAddrs = new DataContainerValue*[argSize];

    /**
     * POPULATE VARIABLE PARAMETER MAPPINGS:
     * Transfer variable address mappings from lists to arrays for indexed access.
     */
    for(int i = 0; i < argSize; i++) {
        methodCalledOriginalPlaceHolderAddrs[i] = methodCalledOriginalPlaceHolderAddrsList.front();
        methodCalledOriginalPlaceHolderAddrsList.pop_front();

        methodCallingOriginalPlaceHolderAddrs[i] = methodCallingOriginalPlaceHolderAddrsList.front();
        methodCallingOriginalPlaceHolderAddrsList.pop_front();
    }

    /**
     * LOCAL VARIABLE AND ARRAY ANALYSIS:
     * Analyze all variables and arrays declared within the function scope
     * (including both parameters and local declarations) to set up complete variable management.
     */
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

    /**
     * ALLOCATE COMPLETE VARIABLE AND ARRAY MANAGEMENT STRUCTURES:
     * Set up arrays for managing all variables and arrays in function scope.
     */
    int totalDataContainerCount = totalVarCount + totalArrCount;
    methodArgDataContainerAddr = new DataContainerValue*[totalDataContainerCount];

    /**
     * POPULATE COMPLETE VARIABLE ADDRESS MAPPING:
     * Store addresses of all variables in the function (parameters + locals).
     */
    for(int i = 0; i < totalDataContainerCount; i++) {
        methodArgDataContainerAddr[i] = methodArgDataContainerAddrList.front();
        methodArgDataContainerAddrList.pop_front();
    }
}

/**
 * Main execution method for function calls.
 * 
 * Orchestrates the complete function call lifecycle through 6 distinct phases:
 * 1. Parameter setup and variable context saving
 * 2. Array parameter setup and local array allocation
 * 3. Function body execution
 * 4. Context restoration preparation
 * 5. Variable restoration and call-by-reference value propagation
 * 6. Array restoration and memory cleanup
 * 
 * This method handles complex stack management required for proper function
 * call semantics, including recursive calls and comprehensive memory management.
 */
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
    /**
     * VARIABLE PARAMETER SETUP:
     * For each variable parameter passed to the function:
     * 1. Save the current value of the function parameter variable (for restoration)
     * 2. Save the current value of the calling argument variable (for restoration)
     * 3. Copy the calling function's argument value to the function parameter
     * 
     * This establishes the parameter passing mechanism while preserving state for restoration.
     */
     // IMPORTANT: delete these pointer at end of stack!!!!
     int totalDataContainerCount = totalVarCount + totalArrCount;
     DataContainerValue* methodArgDataContainerCurrentVal[totalDataContainerCount];
     DataContainerValue* methodCalledDataContainerValue[argSize];
    for (int i = 0; i < argSize; i++) {
#ifdef DEBUG_BUILD
        // Record the argument value being passed for debugging
        debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
        // Save the current value of ALL function variables (for complete restoration)
        methodArgDataContainerCurrentVal[i] = methodArgDataContainerAddr[i]->clone();
        // Save the current value of the function parameter (before receiving new value)
        methodCalledDataContainerValue[i] = methodCalledOriginalPlaceHolderAddrs[i]->clone();
        // Transfer argument value from calling context to function parameter
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

    /**
     * LOCAL VARIABLE STATE PRESERVATION:
     * Save current values of all local variables (non-parameters) so they can be
     * restored after function execution. Local variables are indexed from argSize onwards.
     * 
     * CRITICAL MEMORY SAFETY NOTE:
     * The copyDataContainerValue() method in DoublePtr and ArrayDataContainerValue
     * transfers ownership rather than copying values. This means we must be very
     * careful about the order of operations to prevent double-deletion issues.
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        methodArgDataContainerCurrentVal[i] = methodArgDataContainerAddr[i]->clone();
    }




    // ==================== PHASE 3: FUNCTION BODY EXECUTION ====================
    
    /**
     * COMMAND CHAIN EXECUTION:
     * Execute the function body by traversing the linked command chain.
     * Each command's get() method executes the command and returns the next command to execute.
     * Execution continues until there are no more commands (nullptr is returned).
     * 
     * This forms the core execution loop that processes all statements in the function body.
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

    // ==================== PHASE 4: CONTEXT RESTORATION AND CLEANUP ====================

    /**
     * CRITICAL RESTORATION PHASE:
     * We must restore the calling context regardless of how function execution completed.
     * This is essential for recursive functions and proper stack management.
     *
     * RECURSIVE FUNCTION EXAMPLE:
     * ```
     * func fibonacci(n) {
     *     if (n <= 1) return n;
     *     temp1 = n - 1;
     *     temp2 = n - 2;
     *     return fibonacci(temp1) + fibonacci(temp2);  // Multiple recursive calls
     * }
     * ```
     *
     * Without proper restoration, variables like 'temp1' and 'temp2' would retain
     * values from inner recursive calls, corrupting the outer call's execution.
     */

    // ==================== PHASE 5: VARIABLE RESTORATION AND CALL-BY-REFERENCE VALUE PROPAGATION ====================

    /**
     * LOCAL VARIABLE RESTORATION:
     * Restore all local variables (non-parameters) to their pre-function-call state.
     * This ensures that each function call has isolated local variable scope.
     *
     * MEMORY SAFETY: We use copyDataContainerValue which transfers ownership.
     * The original values in methodArgDataContainerCurrentVal will have their 
     * ownership transferred, so we should not delete them afterward.
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        methodArgDataContainerAddr[i]->copyDataContainerValue(methodArgDataContainerCurrentVal[i]);
    }

    /**
     * VARIABLE PARAMETER RESTORATION AND CALL-BY-REFERENCE HANDLING:
     *
     * This critical phase handles both parameter restoration and call-by-reference semantics.
     * The order of operations is carefully designed to handle recursive function calls correctly.
     *
     * IMPORTANT: This system uses CALL-BY-REFERENCE semantics, NOT return values.
     * All function parameters are passed by reference and their final values are propagated
     * back to the calling context. There is no explicit "return" statement - instead,
     * parameter modifications are the mechanism for passing results back.
     *
     * EXECUTION FLOW EXAMPLE (factorial function):
     * ```
     * func factorial(n) {
     *     if (n <= 1) {
     *         n = 1;  // Set parameter to result value
     *     } else {
     *         temp = n - 1;
     *         factorial(temp);  // Recursive call modifies temp
     *         n = n * temp;     // Set parameter to computed result
     *     }
     * }
     *
     * main() {
     *     x = 5;
     *     factorial(x);  // x will be modified to contain the factorial result
     * }
     * ```
     *
     * STEP-BY-STEP VARIABLE HANDLING:
     * 1. BEFORE factorial(5): x = 5, n = undefined
     * 2. PARAMETER SETUP: n = 5 (copied from x by reference)
     * 3. DURING EXECUTION: n is modified to contain the factorial result
     * 4. RESTORATION: n's final value (120) is propagated back to x
     */

    for(int i = 0; i < argSize; i++) {
        /**
         * FINAL VALUE EXTRACTION:
         * Extract the final value of the function parameter after execution.
         * This captures the computed result that was stored in the parameter
         * during function execution (call-by-reference semantics).
         */
         DataContainerValue* methodArgContainerFinalValue = methodCalledOriginalPlaceHolderAddrs[i]->clone();

        /**
         * FUNCTION PARAMETER RESTORATION:
         * Restore the function parameter to its pre-call state.
         * This is crucial for recursive functions where the same parameter
         * variable is used across multiple call levels.
         * 
         * MEMORY SAFETY: copyDataContainerValue transfers ownership from
         * methodCalledDataContainerValue[i] to the target, so we should not
         * delete methodCalledDataContainerValue[i] afterward.
         */
        methodCalledOriginalPlaceHolderAddrs[i]->copyDataContainerValue(methodCalledDataContainerValue[i]);

        /**
         * CALL-BY-REFERENCE VALUE PROPAGATION:
         * Propagate the final computed value back to the calling context variable.
         * This implements the call-by-reference mechanism where parameter modifications
         * are reflected in the calling context.
         *
         * RECURSIVE FUNCTION CONSIDERATION:
         * In recursive calls, methodCallingOriginalPlaceHolderAddrs[i] might point
         * to the same memory location as methodCalledOriginalPlaceHolderAddrs[i].
         * The careful ordering of operations above prevents corruption in such cases.
         * 
         * MEMORY SAFETY: copyDataContainerValue transfers ownership from
         * methodArgContainerFinalValue to the target.
         */
        methodCallingOriginalPlaceHolderAddrs[i]->copyDataContainerValue(methodArgContainerFinalValue);
        
        /**
         * CLEANUP FINAL VALUE CLONE:
         * Since copyDataContainerValue transferred ownership, we only need to
         * delete the container object, not its contents.
         */
        delete methodArgContainerFinalValue;
    }
    
    // ==================== PHASE 6: MEMORY CLEANUP ====================
    
    /**
     * CLEANUP SAVED DATA CONTAINER VALUES:
     * 
     * CRITICAL MEMORY SAFETY NOTE:
     * Due to the ownership transfer behavior of copyDataContainerValue() in DoublePtr 
     * and ArrayDataContainerValue, we should NOT delete the cloned objects that were
     * used in copyDataContainerValue operations, as their ownership was transferred.
     */
    
    /**
     * CLEANUP PARAMETER DATA CONTAINERS:
     * Clean up the saved parameter values and their corresponding current values.
     * Both types had ownership transfers, so we handle them appropriately.
     */
    for(int i = 0; i < argSize; i++) {
        // These containers transferred ownership during parameter restoration - only delete the container
        delete methodCalledDataContainerValue[i];
        // These containers still own their data since they were not used in copyDataContainerValue - safe to delete normally
        delete methodArgDataContainerCurrentVal[i];
    }
    
    /**
     * CLEANUP LOCAL VARIABLE DATA CONTAINERS:
     * Clean up the saved local variable values that had their ownership transferred during restoration.
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        // These containers transferred ownership during local variable restoration - only delete the container
        delete methodArgDataContainerCurrentVal[i];
    }
}

/**
 * Simplified field setup for built-in functions.
 * 
 * Built-in functions have a streamlined setup process since they don't require
 * the complex parameter mapping and local variable management of user-defined functions.
 * This method sets up direct access to arguments for efficient built-in function execution.
 * 
 * @param map Global map containing rule engine objects for argument resolution
 */
void BuiltInFunctionsImpl::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    std::list<DataContainerValue*> methodArgDataContainerAddrList;

    /**
     * ARGUMENT CATEGORIZATION FOR BUILT-IN FUNCTIONS:
     * Collect all argument data container addresses for unified access.
     * Also count variable and array arguments for potential specialized handling.
     */
    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        AbstractDataContainer* arg = dynamic_cast<AbstractDataContainer*>(map->at(functionCommandInfo->arguments[i]));
        methodArgDataContainerAddrList.push_back(arg->valPtr);
        
        if (dynamic_cast<ArrayRE *>(map->at(functionCommandInfo->arguments[i])) != nullptr) {
            arrCount++;
        } else {
            varCount++;
        }
    }

    /**
     * ALLOCATE UNIFIED DATA CONTAINER ACCESS ARRAY:
     * Create array for direct argument access using the unified DataContainerValue approach.
     */
    methodArgDataContainerAddr = new DataContainerValue*[functionCommandInfo->argumentsSize];

    /**
     * POPULATE UNIFIED DATA CONTAINER ACCESS:
     * Store all argument data container addresses for direct access by built-in functions.
     */
    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        methodArgDataContainerAddr[i] = methodArgDataContainerAddrList.front();
        methodArgDataContainerAddrList.pop_front();
    }
}

/**
 * NINF (Negative Infinity) Built-in Function Implementation.
 * 
 * Sets the target variable or all elements of an array to negative infinity.
 * Supports both single variable and single array arguments.
 * 
 * Usage:
 * - NINF(variable) → variable = -∞
 * - NINF(array) → all array elements = -∞
 */
void NINF::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            *(doublePtr->value) = -std::numeric_limits<double>::infinity();
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayValue* arrayValue = dynamic_cast<ArrayValue*>(dataContainerValue)) {
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = -std::numeric_limits<double>::infinity();
            }
        }
    }
}

/**
 * PINF (Positive Infinity) Built-in Function Implementation.
 * 
 * Sets the target variable or all elements of an array to positive infinity.
 * Supports both single variable and single array arguments.
 * 
 * Usage:
 * - PINF(variable) → variable = +∞
 * - PINF(array) → all array elements = +∞
 */
void PINF::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            *(doublePtr->value) = std::numeric_limits<double>::infinity();
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayValue* arrayValue = dynamic_cast<ArrayValue*>(dataContainerValue)) {
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = std::numeric_limits<double>::infinity();
            }
        }
    }
}

// Static random number generation components for RAND function
static std::random_device rd;  // Non-deterministic random seed
static std::mt19937 gen(rd()); // Mersenne Twister generator engine
static std::uniform_real_distribution<> dis(0.0, 1.0); // Uniform distribution [0.0, 1.0)

/**
 * RAND (Random Number Generation) Built-in Function Implementation.
 * 
 * Generates random numbers in the range [0.0, 1.0) using the Mersenne Twister algorithm.
 * Supports both single variable and single array arguments.
 * 
 * Random Number Quality:
 * - Uses std::random_device for non-deterministic seeding
 * - Employs Mersenne Twister (MT19937) for high-quality pseudorandom generation
 * - Uniform distribution ensures equal probability across the range
 * 
 * Usage:
 * - RAND(variable) → variable = random value in [0.0, 1.0)
 * - RAND(array) → all array elements = independent random values in [0.0, 1.0)
 */
void RAND::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            *(doublePtr->value) = dis(gen);
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayValue* arrayValue = dynamic_cast<ArrayValue*>(dataContainerValue)) {
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = dis(gen);
            }
        }
    }
}

// ==================== MATHEMATICAL BUILT-IN FUNCTIONS ====================

/**
 * ABS (Absolute Value) Built-in Function Implementation.
 * 
 * Computes the absolute value of the input, ensuring a non-negative result.
 * Modifies the input variable in-place with its absolute value.
 * 
 * Mathematical Definition: |x| = x if x ≥ 0, -x if x < 0
 * 
 * Usage: ABS(variable) → variable = |variable|
 * Example: ABS(-5.5) → variable becomes 5.5
 */
void ABS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::abs(*(doublePtr->value));
    }
}

// ==================== TRIGONOMETRIC BUILT-IN FUNCTIONS ====================

/**
 * SIN (Sine) Built-in Function Implementation.
 * 
 * Computes the sine of the input angle (in radians).
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: [-1, 1]
 * - Period: 2π
 * 
 * Usage: SIN(variable) → variable = sin(variable)
 * Example: SIN(π/2) → variable becomes 1.0
 */
void SIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::sin(*(doublePtr->value));
    }
}

/**
 * COS (Cosine) Built-in Function Implementation.
 * 
 * Computes the cosine of the input angle (in radians).
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: [-1, 1]
 * - Period: 2π
 * 
 * Usage: COS(variable) → variable = cos(variable)
 * Example: COS(0) → variable becomes 1.0
 */
void COS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::cos(*(doublePtr->value));
    }
}

/**
 * TAN (Tangent) Built-in Function Implementation.
 * 
 * Computes the tangent of the input angle (in radians).
 * 
 * Mathematical Properties:
 * - Domain: All real numbers except (π/2 + nπ) where n is any integer
 * - Range: (-∞, ∞)
 * - Period: π
 * - Undefined at odd multiples of π/2
 * 
 * Usage: TAN(variable) → variable = tan(variable)
 * Example: TAN(π/4) → variable becomes 1.0
 */
void TAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::tan(*(doublePtr->value));
    }
}

// ==================== INVERSE TRIGONOMETRIC BUILT-IN FUNCTIONS ====================

/**
 * ASIN (Arcsine/Inverse Sine) Built-in Function Implementation.
 * 
 * Computes the arcsine (inverse sine) of the input value.
 * Returns the angle whose sine equals the input value.
 * 
 * Mathematical Properties:
 * - Domain: [-1, 1]
 * - Range: [-π/2, π/2]
 * - Input outside domain results in NaN
 * 
 * Usage: ASIN(variable) → variable = asin(variable)
 * Example: ASIN(0.5) → variable becomes π/6 ≈ 0.5236
 */
void ASIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::asin(*(doublePtr->value));
    }
}

/**
 * ACOS (Arccosine/Inverse Cosine) Built-in Function Implementation.
 * 
 * Computes the arccosine (inverse cosine) of the input value.
 * Returns the angle whose cosine equals the input value.
 * 
 * Mathematical Properties:
 * - Domain: [-1, 1]
 * - Range: [0, π]
 * - Input outside domain results in NaN
 * 
 * Usage: ACOS(variable) → variable = acos(variable)
 * Example: ACOS(0.5) → variable becomes π/3 ≈ 1.0472
 */
void ACOS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::acos(*(doublePtr->value));
    }
}

/**
 * ATAN (Arctangent/Inverse Tangent) Built-in Function Implementation.
 * 
 * Computes the arctangent (inverse tangent) of the input value.
 * Returns the angle whose tangent equals the input value.
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: (-π/2, π/2)
 * - Well-defined for all finite real numbers
 * 
 * Usage: ATAN(variable) → variable = atan(variable)
 * Example: ATAN(1.0) → variable becomes π/4 ≈ 0.7854
 */
void ATAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::atan(*(doublePtr->value));
    }
}

// ==================== ROUNDING AND CEILING BUILT-IN FUNCTIONS ====================

/**
 * FLOOR (Floor Function) Built-in Function Implementation.
 * 
 * Computes the largest integer less than or equal to the input value.
 * Always rounds towards negative infinity.
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: All integers as floating-point numbers
 * - floor(x) ≤ x < floor(x) + 1
 * 
 * Usage: FLOOR(variable) → variable = floor(variable)
 * Examples:
 * - FLOOR(3.7) → variable becomes 3.0
 * - FLOOR(-2.3) → variable becomes -3.0
 */
void FLOOR::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::floor(*(doublePtr->value));
    }
}

/**
 * CEIL (Ceiling Function) Built-in Function Implementation.
 * 
 * Computes the smallest integer greater than or equal to the input value.
 * Always rounds towards positive infinity.
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: All integers as floating-point numbers
 * - ceil(x) - 1 < x ≤ ceil(x)
 * 
 * Usage: CEIL(variable) → variable = ceil(variable)
 * Examples:
 * - CEIL(3.2) → variable becomes 4.0
 * - CEIL(-2.8) → variable becomes -2.0
 */
void CEIL::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::ceil(*(doublePtr->value));
    }
}

// ==================== EXPONENTIAL AND POWER BUILT-IN FUNCTIONS ====================

/**
 * EXP (Exponential Function) Built-in Function Implementation.
 * 
 * Computes e raised to the power of the input value (e^x).
 * Uses Euler's number e ≈ 2.71828 as the base.
 * 
 * Mathematical Properties:
 * - Domain: (-∞, ∞)
 * - Range: (0, ∞)
 * - exp(0) = 1
 * - exp(1) = e ≈ 2.71828
 * - Inverse function of natural logarithm
 * 
 * Usage: EXP(variable) → variable = e^variable
 * Example: EXP(1.0) → variable becomes e ≈ 2.71828
 */
void EXP::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::exp(*(doublePtr->value));
    }
}

/**
 * SQRT (Square Root) Built-in Function Implementation.
 * 
 * Computes the positive square root of the input value.
 * 
 * Mathematical Properties:
 * - Domain: [0, ∞) (non-negative real numbers)
 * - Range: [0, ∞)
 * - sqrt(x * x) = |x| for all real x
 * - Negative inputs result in NaN
 * 
 * Usage: SQRT(variable) → variable = √variable
 * Example: SQRT(9.0) → variable becomes 3.0
 */
void SQRT::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        *(doublePtr->value) = std::sqrt(*(doublePtr->value));
    }
}

/**
 * POW (Power Function) Built-in Function Implementation.
 * 
 * Computes the first argument raised to the power of the second argument (base^exponent).
 * Requires exactly two variable arguments and modifies the first with the result.
 * 
 * Mathematical Properties:
 * - Domain: Depends on base and exponent values
 * - For positive base: all real exponents allowed
 * - For zero base: positive exponents allowed
 * - For negative base: integer exponents recommended
 * 
 * Special Cases:
 * - pow(x, 0) = 1 for any x ≠ 0
 * - pow(0, y) = 0 for any y > 0
 * - pow(1, y) = 1 for any finite y
 * 
 * Usage: POW(base, exponent) → base = base^exponent
 * Example: POW(2.0, 3.0) → first variable becomes 8.0
 */
void POW::process() {
    if(functionCommandInfo->argumentsSize >= 2) {
        DoublePtr* doublePtr1 = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        DoublePtr* doublePtr2 = static_cast<DoublePtr*>(methodArgDataContainerAddr[1]);
        *(doublePtr1->value) = std::pow(*(doublePtr1->value), *(doublePtr2->value));
    }
}