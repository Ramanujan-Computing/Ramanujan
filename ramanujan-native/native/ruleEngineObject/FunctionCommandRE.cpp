//
// Created by pranav on 28/3/24.
//

#include "FunctionCommandRE.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
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

    std::list<double*> methodCalledOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCalledArrayPlaceHolderAddrsList;

    std::list<double*> methodCallingOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCallingArrayPlaceHolderAddrsList;

    /**
     * ARGUMENT CATEGORIZATION LOOP:
     * Iterate through all function parameters to separate variables from arrays
     * and establish bidirectional address mappings between caller and callee contexts.
     */
    for(int i = 0; i < functionInfoRE->argSize; i++) {
        if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
            // Array parameter found
            arrCount++;
            methodCalledArrayPlaceHolderAddrsList.push_back(((ArrayRE*)functionInfoRE->arguments[i])->getValPtr());
            methodCallingArrayPlaceHolderAddrsList.push_back(((ArrayRE*)map->at(functionCommandInfo->arguments[i]))->getValPtr());
            
            // Build name mapping for debugging purposes
            arrayNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            // Variable parameter found
            varCount++;
            methodCalledOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)functionInfoRE->arguments[i])->getValPtrPtr());
            methodCallingOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)map->at(functionCommandInfo->arguments[i]))->getValPtrPtr());
        }
    }

    /**
     * MEMORY ALLOCATION FOR PARAMETER MAPPING ARRAYS:
     * Allocate arrays to store address mappings for efficient parameter passing.
     */
    methodCallingOriginalPlaceHolderAddrs = new double*[varCount];
    methodCallingArrayPlaceHolderAddrs = new ArrayValue**[arrCount];
    methodCalledOriginalPlaceHolderAddrs = new double*[varCount];
    methodCalledArrayPlaceHolderAddrs = new ArrayValue**[arrCount];

    /**
     * POPULATE VARIABLE PARAMETER MAPPINGS:
     * Transfer variable address mappings from lists to arrays for indexed access.
     */
    for(int i = 0; i < varCount; i++) {
        methodCalledOriginalPlaceHolderAddrs[i] = methodCalledOriginalPlaceHolderAddrsList.front();
        methodCalledOriginalPlaceHolderAddrsList.pop_front();

        methodCallingOriginalPlaceHolderAddrs[i] = methodCallingOriginalPlaceHolderAddrsList.front();
        methodCallingOriginalPlaceHolderAddrsList.pop_front();
    }

    /**
     * POPULATE ARRAY PARAMETER MAPPINGS:
     * Transfer array address mappings from lists to arrays for indexed access.
     */
    for(int i = 0; i < arrCount; i++) {
        methodCalledArrayPlaceHolderAddrs[i] = methodCalledArrayPlaceHolderAddrsList.front();
        methodCalledArrayPlaceHolderAddrsList.pop_front();

        methodCallingArrayPlaceHolderAddrs[i] = methodCallingArrayPlaceHolderAddrsList.front();
        methodCallingArrayPlaceHolderAddrsList.pop_front();
    }

    /**
     * LOCAL VARIABLE AND ARRAY ANALYSIS:
     * Analyze all variables and arrays declared within the function scope
     * (including both parameters and local declarations) to set up complete variable management.
     */
    std::list<double*> methodArgVariableAddrList;
    std::list<ArrayValue**> methodArgArrayAddrList;

    for(int i = 0; i < functionInfoRE->functionCall->allVariablesInMethodSize; i++) {
        if(dynamic_cast<ArrayRE*>(functionInfoRE->allVariablesInMethod[i]) != nullptr) {
            totalArrCount++;
            methodArgArrayAddrList.push_back(((ArrayRE*)functionInfoRE->allVariablesInMethod[i])->getValPtr());
        } else {
            totalVarCount++;
            methodArgVariableAddrList.push_back(((DoublePtr*)functionInfoRE->allVariablesInMethod[i])->getValPtrPtr());
        }
    }

    /**
     * ALLOCATE COMPLETE VARIABLE AND ARRAY MANAGEMENT STRUCTURES:
     * Set up arrays for managing all variables and arrays in function scope.
     */
    methodArgVariableAddr = new double*[totalVarCount];
    methodArgArrayAddr = new double**[totalArrCount];
    methodArgArrayTotalSize = new int[totalArrCount];

    /**
     * POPULATE COMPLETE VARIABLE ADDRESS MAPPING:
     * Store addresses of all variables in the function (parameters + locals).
     */
    for(int i = 0; i < totalVarCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }
    
    /**
     * POPULATE COMPLETE ARRAY ADDRESS MAPPING:
     * Store addresses and sizes of all arrays in the function (parameters + locals).
     */
    for(int i = 0; i < totalArrCount; i++) {
        methodArgArrayAddr[i] = &(*methodArgArrayAddrList.front())->val;
        methodArgArrayTotalSize[i] = (*methodArgArrayAddrList.front())->totalSize;
        methodArgArrayAddrList.pop_front();
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
    // ==================== DEBUG SETUP ====================
#ifdef DEBUG_BUILD
    // Get debug point for tracking function call execution
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif

    // ==================== PHASE 1: PARAMETER SETUP AND VARIABLE CONTEXT SAVING ====================
    
    /**
     * VARIABLE PARAMETER SETUP:
     * For each variable parameter passed to the function:
     * 1. Save the current value of the function parameter variable (for restoration)
     * 2. Save the current value of the calling argument variable (for restoration)
     * 3. Copy the calling function's argument value to the function parameter
     * 
     * This establishes the parameter passing mechanism while preserving state for restoration.
     */
    double   methodArgVariableCurrentVal[totalVarCount];
    double methodCalledVariablValue[varCount];
    for (int i = 0; i < varCount; i++) {
#ifdef DEBUG_BUILD
        // Record the argument value being passed for debugging
        debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
        // Save the current value of ALL function variables (for complete restoration)
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
        // Save the current value of the function parameter (before receiving new value)
        methodCalledVariablValue[i] = *methodCalledOriginalPlaceHolderAddrs[i];
        // Transfer argument value from calling context to function parameter
        *methodCalledOriginalPlaceHolderAddrs[i] = (*methodCallingOriginalPlaceHolderAddrs[i]);
    }

#ifdef DEBUG_BUILD
    // Record array name mappings for debugging purposes
    // This helps track which calling arrays correspond to which function parameters
    for(auto it = arrayNameMethodMap.begin(); it != arrayNameMethodMap.end(); it++) {
        debugPoint->addArrayInFuncCall(it->first, it->second);
    }
    debugger->commitDebugPoint();
#endif

    /**
     * LOCAL VARIABLE STATE PRESERVATION:
     * Save current values of all local variables (non-parameters) so they can be
     * restored after function execution. Local variables are indexed from varCount onwards.
     */
    for(int i = varCount; i < totalVarCount; i++) {
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
    }


    // ==================== PHASE 2: ARRAY PARAMETER SETUP AND LOCAL ARRAY ALLOCATION ====================
    
    /**
     * ARRAY PARAMETER AND LOCAL ARRAY SETUP:
     * Handle both array parameters (passed by reference) and local arrays (dynamically allocated).
     * Array parameters point to calling arrays, while local arrays get fresh memory allocation.
     */
    double* methodArgArrayCurrentVal[totalArrCount];
    ArrayValue* methodCalledArrayValue[arrCount];
    
    /**
     * ARRAY PARAMETER SETUP LOOP:
     * For each array parameter (i = 0 to arrCount-1):
     * Set up pass-by-reference semantics for array parameters.
     */
    for(int i = 0; i < arrCount; i++) {
        // Save the current array pointer for complete restoration after function execution
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];

        // Save the current array reference in the function parameter (before receiving new reference)
        methodCalledArrayValue[i] = *methodCalledArrayPlaceHolderAddrs[i];
        
        // Establish pass-by-reference: function parameter array now points to calling array
        *methodCalledArrayPlaceHolderAddrs[i] = *methodCallingArrayPlaceHolderAddrs[i];
    }

    /**
     * LOCAL ARRAY ALLOCATION LOOP:
     * For local arrays (non-parameters) (i = arrCount to totalArrCount-1):
     * Allocate fresh memory for arrays declared within the function.
     */
    for(int i = arrCount; i < totalArrCount; i++) {
        // Save current array pointer (likely nullptr for local arrays)
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];
        // Allocate new memory for local array with its specified size
        *methodArgArrayAddr[i] = new double[methodArgArrayTotalSize[i]];
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
     * Index Range: i = varCount to totalVarCount-1 (local variables only)
     */
    for(int i = varCount; i < totalVarCount; i++) {
        *methodArgVariableAddr[i] = methodArgVariableCurrentVal[i];
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
    
    for(int i = 0; i < varCount; i++) {
        /**
         * FINAL VALUE EXTRACTION:
         * Extract the final value of the function parameter after execution.
         * This captures the computed result that was stored in the parameter
         * during function execution (call-by-reference semantics).
         */
        double methodArgFinalValue = *methodCalledOriginalPlaceHolderAddrs[i];
        
        /**
         * FUNCTION PARAMETER RESTORATION:
         * Restore the function parameter to its pre-call state.
         * This is crucial for recursive functions where the same parameter
         * variable is used across multiple call levels.
         */
        *methodCalledOriginalPlaceHolderAddrs[i] = methodCalledVariablValue[i];
        
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
         */
        *methodCallingOriginalPlaceHolderAddrs[i] = methodArgFinalValue;
    }
    // ==================== PHASE 6: ARRAY RESTORATION AND MEMORY CLEANUP ====================

    /**
     * LOCAL ARRAY CLEANUP AND RESTORATION:
     * Handle memory deallocation for local arrays and pointer restoration.
     * This phase is critical for preventing memory leaks and maintaining proper array state.
     * 
     * Index Range: i = arrCount to totalArrCount-1 (local arrays only)
     */
    for(int i = arrCount; i < totalArrCount; i++) {
        /**
         * MEMORY DEALLOCATION:
         * Free the dynamically allocated memory for local arrays.
         * This prevents memory leaks that would accumulate with each function call.
         * 
         * Memory allocated in Phase 2 with: new double[methodArgArrayTotalSize[i]]
         * Now freed with: delete[] to match the allocation method.
         */
        delete[] *methodArgArrayAddr[i];

        /**
         * POINTER RESTORATION:
         * Restore the array pointer to its pre-function-call value.
         * This ensures clean state for subsequent function calls and recursive safety.
         */
        *methodArgArrayAddr[i] = methodArgArrayCurrentVal[i];
    }
    
    /**
     * ARRAY PARAMETER RESTORATION:
     * Restore array parameter references to maintain proper calling context.
     * Unlike local arrays, parameter arrays are not deallocated since they reference
     * memory owned by the calling context.
     * 
     * ARRAY PARAMETER FLOW EXAMPLE:
     * ```
     * func processArray(arr) {
     *     arr[0] = arr[0] + 1;  // Modify the array
     *     processArray(arr);    // Recursive call with same array
     * }
     * 
     * main() {
     *     myArray[5] = {1, 2, 3, 4, 5};
     *     processArray(myArray);  // Pass array by reference
     * }
     * ```
     * 
     * In this scenario:
     * - 'arr' parameter points to 'myArray' memory
     * - Modifications to 'arr' directly affect 'myArray'
     * - No memory allocation/deallocation needed for 'arr'
     * - Only pointer restoration required for proper stack management
     */
    for(int i = 0; i < arrCount; i++) {
        /**
         * FINAL ARRAY REFERENCE EXTRACTION:
         * Extract the final array reference after function execution.
         * In call-by-reference semantics, arrays may be reassigned to point
         * to different memory locations during function execution.
         */
        auto methodArgFinalArrayRef = *methodCalledArrayPlaceHolderAddrs[i];
        
        /**
         * FUNCTION ARRAY PARAMETER RESTORATION:
         * Restore the function's array parameter to its pre-call reference.
         * Essential for recursive functions using the same array parameter variable.
         */
        *methodCalledArrayPlaceHolderAddrs[i] = methodCalledArrayValue[i];
        
        /**
         * CALL-BY-REFERENCE ARRAY PROPAGATION:
         * Propagate the final array reference back to calling context.
         * This maintains proper array reference semantics across function calls.
         * Note: Array contents are already modified in-place due to reference sharing.
         */
        *methodCallingArrayPlaceHolderAddrs[i] = methodArgFinalArrayRef;
    }

    // Function execution complete - calling context fully restored
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
    std::list<double *> methodArgVariableAddrList;
    std::list<ArrayValue **> methodArgArrayAddrList;

    /**
     * ARGUMENT CATEGORIZATION FOR BUILT-IN FUNCTIONS:
     * Separate variable and array arguments to enable direct access patterns
     * used by built-in function implementations.
     */
    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        auto arg = map->at(functionCommandInfo->arguments[i]);
        if (dynamic_cast<ArrayRE *>(arg) != nullptr) {
            arrCount++;
            methodArgArrayAddrList.push_back(((ArrayRE *) arg)->getValPtr());
        } else {
            varCount++;
            methodArgVariableAddrList.push_back(((DoublePtr *) arg)->getValPtrPtr());
        }
    }

    /**
     * ALLOCATE DIRECT ACCESS ARRAYS:
     * Create arrays for direct argument access (no complex mapping needed).
     */
    methodArgVariableAddr = new double *[varCount];
    methodArgArrayAddr = new ArrayValue **[arrCount];

    /**
     * POPULATE DIRECT VARIABLE ACCESS:
     * Store variable argument addresses for direct modification by built-in functions.
     */
    for (int i = 0; i < varCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }

    /**
     * POPULATE DIRECT ARRAY ACCESS:
     * Store array argument addresses for direct modification by built-in functions.
     */
    for (int i = 0; i < arrCount; i++) {
        methodArgArrayAddr[i] = methodArgArrayAddrList.front();
        methodArgArrayAddrList.pop_front();
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
    // Handle single variable argument
    if(varCount == 1) {
        *methodArgVariableAddr[0] = -std::numeric_limits<double>::infinity();
    }

    // Handle single array argument
    if(arrCount == 1) {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = -std::numeric_limits<double>::infinity();
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
    // Handle single variable argument
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::numeric_limits<double>::infinity();
    }

    // Handle single array argument
    if(arrCount == 1) {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = std::numeric_limits<double>::infinity();
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
    // Handle single variable argument
    if(varCount == 1) {
        *methodArgVariableAddr[0] = dis(gen);
    }

    // Handle single array argument
    if(arrCount == 1) {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = dis(gen);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::abs(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sin(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::cos(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::tan(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::asin(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::acos(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::atan(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::floor(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::ceil(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::exp(*methodArgVariableAddr[0]);
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
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sqrt(*methodArgVariableAddr[0]);
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
    if(varCount == 2) {
        *methodArgVariableAddr[0] = std::pow(*methodArgVariableAddr[0], *methodArgVariableAddr[1]);
    }
}