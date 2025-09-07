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
     * Demarcate the functionInfoRE->argument in variables and arrays.
     */

    std::list<double*> methodCalledOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCalledArrayPlaceHolderAddrsList;

    std::list<double*> methodCallingOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCallingArrayPlaceHolderAddrsList;

    for(int i = 0; i < functionInfoRE->argSize; i++) {
        if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
            arrCount++;
            methodCalledArrayPlaceHolderAddrsList.push_back(((ArrayRE*)functionInfoRE->arguments[i])->getValPtr());
            methodCallingArrayPlaceHolderAddrsList.push_back(((ArrayRE*)map->at(functionCommandInfo->arguments[i]))->getValPtr());
            arrayNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            varCount++;
            methodCalledOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)functionInfoRE->arguments[i])->getValPtrPtr());
            methodCallingOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)map->at(functionCommandInfo->arguments[i]))->getValPtrPtr());
        }
    }

    methodCallingOriginalPlaceHolderAddrs = new double*[varCount];
    methodCallingArrayPlaceHolderAddrs = new ArrayValue**[arrCount];
    methodCalledOriginalPlaceHolderAddrs = new double*[varCount];
    methodCalledArrayPlaceHolderAddrs = new ArrayValue**[arrCount];

    for(int i = 0; i < varCount; i++) {
        methodCalledOriginalPlaceHolderAddrs[i] = methodCalledOriginalPlaceHolderAddrsList.front();
        methodCalledOriginalPlaceHolderAddrsList.pop_front();

        methodCallingOriginalPlaceHolderAddrs[i] = methodCallingOriginalPlaceHolderAddrsList.front();
        methodCallingOriginalPlaceHolderAddrsList.pop_front();
    }

    for(int i = 0; i < arrCount; i++) {
        methodCalledArrayPlaceHolderAddrs[i] = methodCalledArrayPlaceHolderAddrsList.front();
        methodCalledArrayPlaceHolderAddrsList.pop_front();

        methodCallingArrayPlaceHolderAddrs[i] = methodCallingArrayPlaceHolderAddrsList.front();
        methodCallingArrayPlaceHolderAddrsList.pop_front();
    }

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

    methodArgVariableAddr = new double*[totalVarCount];
    methodArgArrayAddr = new double**[totalArrCount];
    methodArgArrayTotalSize = new int[totalArrCount];

    for(int i = 0; i < totalVarCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }
    for(int i = 0; i < totalArrCount; i++) {
        methodArgArrayAddr[i] = &(*methodArgArrayAddrList.front())->val;
        methodArgArrayTotalSize[i] = (*methodArgArrayAddrList.front())->totalSize;
        methodArgArrayAddrList.pop_front();
    }
}

void FunctionCommandRE::process() {
    // ==================== DEBUG SETUP ====================
#ifdef DEBUG_BUILD
    // Get debug point for tracking function call execution
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif

    // ==================== PHASE 1: PARAMETER SETUP AND VARIABLE CONTEXT SAVING ====================
    
    /*
     * For each variable parameter passed to the function:
     * 1. Save the current value of the called function's parameter variable (for stack restoration)
     * 2. Copy the calling function's argument value to the called function's parameter
     * 3. Save the current state of the parameter variable for later restoration
     */
    double methodArgVariableCurrentVal[totalVarCount];
    double methodCalledVariablValue[varCount];
    for (int i = 0; i < varCount; i++) {
#ifdef DEBUG_BUILD
        // Record the current function variable value for debugging
        debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
        // Save the current value of the parameter variable for restoration after function execution
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
        // Save the current value of the called function's parameter variable
        methodCalledVariablValue[i] = *methodCalledOriginalPlaceHolderAddrs[i];
        // Copy the argument value from calling context to called function parameter
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

    /*
     * For local variables in the function (non-parameters):
     * Save their current values so they can be restored after function execution.
     * These variables start from index varCount (after the parameters).
     */
    for(int i=varCount; i < totalVarCount; i++) {
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
    }


    // ==================== PHASE 2: ARRAY PARAMETER SETUP ====================
    
    /*
     * For each array parameter passed to the function:
     * 1. Save the current array reference in the called function (for stack restoration)
     * 2. Point the called function's parameter array to the calling function's argument array
     * 3. Save the current array pointer for later restoration
     */
    double* methodArgArrayCurrentVal[totalArrCount];
    ArrayValue* methodCalledArrayValue[arrCount];
    for(int i = 0; i < arrCount; i++) {

        // Save the current array pointer for restoration after function execution
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];

        // Save the current array pointer in the called function
        methodCalledArrayValue[i] = *methodCalledArrayPlaceHolderAddrs[i];
        
        // Point the called function's parameter array to the calling function's argument array
        *methodCalledArrayPlaceHolderAddrs[i] = *methodCallingArrayPlaceHolderAddrs[i];
    }

    /*
     * For local arrays in the function (non-parameters):
     * 1. Save their current array pointers
     * 2. Allocate new memory for local arrays with their specified sizes
     * These arrays start from index arrCount (after the parameters).
     */
    for(int i=arrCount;i< totalArrCount;i++) {
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];
        // Allocate new array for local array variables
        *methodArgArrayAddr[i] = new double[methodArgArrayTotalSize[i]];
    }

    // ==================== PHASE 3: FUNCTION BODY EXECUTION ====================
    
    /*
     * Execute the function body by traversing the command chain.
     * Each command returns the next command to execute, forming a linked execution chain.
     * Execution continues until there are no more commands (nullptr).
     */
    CommandRE* command = firstCommand;
    while(command != nullptr) {
        command = command->get();  // Execute current command and get next command
    }

    // ==================== PHASE 4: CONTEXT RESTORATION AND CLEANUP ====================
    
    /*
     * CRITICAL: We must restore the calling context regardless of function execution outcome.
     * This is essential for recursive functions and proper stack management.
     * 
     * Example scenario requiring restoration:
     * func(a,b) {
     *      c = a + 1
     *      func(c,b)    // Recursive call modifies 'a'
     *      d = a + 1    // Without restoration, 'a' would have value of 'c'
     * }
     */

    // ==================== PHASE 5: VARIABLE RESTORATION ====================

    /*
     * Local Variable Restoration Loop:
     * For local variables (non-parameters) (i = varCount to totalVarCount-1):
     *
     * WHAT HAPPENS:
     * These are variables declared within the function that are not parameters.
     *
     * EXAMPLE:
     * In factorial function: 'temp' is a local variable
     * - Before function: temp = some_previous_value (or 0.0 if first time)
     * - During function: temp = n - 1
     * - After restoration: temp = some_previous_value
     *
     * WHY NEEDED:
     * If factorial(5) calls factorial(4), and factorial(4) has its own 'temp',
     * we need to restore factorial(5)'s 'temp' when factorial(4) completes.
     */
    for(int i =varCount; i<totalVarCount;i++)
    {
        *methodArgVariableAddr[i] = methodArgVariableCurrentVal[i];
    }
    //delete[] methodArgVariableCurrentVal;
    
    /*
     * DETAILED VARIABLE RESTORATION EXPLANATION:
     * 
     * The restoration process is critical for maintaining proper function call semantics,
     * especially for recursive functions. Here's what happens with concrete examples:
     * 
     * EXAMPLE CODE SCENARIO:
     * ```
     * func factorial(n) {
     *     if (n <= 1) return 1;
     *     temp = n - 1;
     *     return n * factorial(temp);  // Recursive call
     * }
     * 
     * main() {
     *     x = 5;
     *     result = factorial(x);  // Call with x=5
     * }
     * ```
     * 
     * FIELD CHANGES DURING EXECUTION:
     * 
     * 1. BEFORE CALL factorial(5):
     *    - Main context: x = 5
     *    - Function parameter 'n' is uninitialized
     * 
     * 2. DURING PARAMETER SETUP:
     *    - methodCallingOriginalPlaceHolderAddrs[0] points to 'x' (value: 5)
     *    - methodCalledOriginalPlaceHolderAddrs[0] points to 'n' (will receive 5)
     *    - variableStackCurrent[0] saves old value of 'n' (for restoration)
     *    - *methodCalledOriginalPlaceHolderAddrs[0] = 5 (n becomes 5)
     * 
     * 3. DURING FUNCTION EXECUTION:
     *    - 'n' = 5, 'temp' gets allocated and set to 4
     *    - When factorial(4) is called recursively:
     *      - 'n' temporarily becomes 4 for the inner call
     *      - Original 'n' (5) is saved in stack
     * 
     * 4. DURING RESTORATION (this phase):
     *    - Inner call completes, 'n' needs to be restored to 5
     *    - All local variables need to be reset to their pre-call state
     */
    
    /*
     * Variable Parameter Restoration Loop:
     * For each variable parameter (i = 0 to varCount-1):
     */
    for(int i = 0; i < varCount; i++) {
        /*
         * Step 2: Restore the calling context variable to its stack-saved value
         * 
         * WHAT HAPPENS:
         * - methodCallingOriginalPlaceHolderAddrs[i] points to the argument variable in calling context
         * - variableStackCurrent[i] contains the calling context's variable value from Phase 4
         * 
         * EXAMPLE:
         * If this is factorial(4) completing and returning to factorial(5):
         * - methodCallingOriginalPlaceHolderAddrs[0] points to 'temp' in factorial(5) context
         * - variableStackCurrent[0] contains the value of 'temp' (4)
         * - *methodCallingOriginalPlaceHolderAddrs[0] = 4 (restore 'temp' to 4)
         * 
         * This ensures that when factorial(4) returns, the factorial(5) context
         * has 'temp' = 4 (not some corrupted value from the inner call)
         */
        double methodArgReturnVal = *methodCalledOriginalPlaceHolderAddrs[i];
        *methodCalledOriginalPlaceHolderAddrs[i] = methodCalledVariablValue[i];
        /*
         * Why not set methodCallingOriginalPlaceHolderAddrs to methodCallingOriginalPlaceHolderAddrs, and then
         * set methodCalledOriginalPlaceHolderAddrs to methodArgReturnVal?
         *
         * Because in the case of recursive functions, methodCallingOriginalPlaceHolderAddrs[i] might be
         * the same as methodCalledOriginalPlaceHolderAddrs[i].
         *
         */
        *methodCallingOriginalPlaceHolderAddrs[i] = methodArgReturnVal;
    }
    


    // ==================== PHASE 6: ARRAY RESTORATION AND CLEANUP ====================

    /*
 * Local Array Cleanup and Restoration Loop:
 * For local arrays (non-parameters) (i = arrCount to totalArrCount-1):
 *
 * CRITICAL MEMORY MANAGEMENT:
 * Local arrays are dynamically allocated during function execution and must be
 * properly cleaned up to prevent memory leaks.
 */

    for(int i=arrCount;i< totalArrCount;i++) {
        /*
         * Step 1: Free the dynamically allocated memory for local arrays
         *
         * WHAT HAPPENS:
         * - *methodArgArrayAddr[i] points to the dynamically allocated array memory
         * - This memory was allocated in Phase 2 with: new double[methodArgArrayTotalSize[i]]
         * - delete[] frees this memory to prevent memory leaks
         *
         * EXAMPLE:
         * - Local array 'localArr' was allocated with: new double[10]
         * - *methodArgArrayAddr[1] points to this allocated memory
         * - delete[] *methodArgArrayAddr[1] frees the 10*sizeof(double) bytes
         *
         * MEMORY LEAK PREVENTION:
         * Without this cleanup, each function call would leak memory equal to
         * the size of all local arrays, leading to unbounded memory growth.
         */
        delete[] *methodArgArrayAddr[i];

        /*
         * Step 2: Restore the array pointer to its pre-function-call value
         *
         * WHAT HAPPENS:
         * - methodArgArrayCurrentVal[i] contains the saved array pointer from Phase 2
         * - This restores the array pointer to whatever it pointed to before function execution
         *
         * EXAMPLE:
         * - Before function: 'localArr' pointer = nullptr (or previous value)
         * - During function: 'localArr' pointer = allocated_memory
         * - After restoration: 'localArr' pointer = nullptr (restored)
         *
         * RECURSIVE CALL SAFETY:
         * This ensures that recursive calls don't interfere with each other's
         * local array allocations and each call has clean local array state.
         */
        *methodArgArrayAddr[i] = methodArgArrayCurrentVal[i];
    }
    //delete[] methodArgArrayCurrentVal;
    /*
     * DETAILED ARRAY RESTORATION EXPLANATION:
     * 
     * Arrays have more complex restoration because they involve pointer management
     * and dynamic memory allocation.
     * 
     * EXAMPLE CODE SCENARIO:
     * ```
     * func processArray(arr, size) {
     *     localArr[10];  // Local array
     *     // Process arrays...
     *     processArray(arr, size-1);  // Recursive call
     * }
     * 
     * main() {
     *     mainArray[20] = {1,2,3,...};
     *     processArray(mainArray, 20);
     * }
     * ```
     * 
     * FIELD CHANGES FOR ARRAYS:
     * 
     * 1. PARAMETER ARRAYS (i = 0 to arrCount-1):
     *    - methodCallingArrayPlaceHolderAddrs[i] points to 'mainArray' in main()
     *    - methodCalledArrayPlaceHolderAddrs[i] points to 'arr' parameter
     *    - During function: 'arr' points to same memory as 'mainArray'
     * 
     * 2. LOCAL ARRAYS (i = arrCount to totalArrCount-1):
     *    - methodArgArrayAddr[i] points to 'localArr' pointer
     *    - During function: new memory allocated for 'localArr'
     *    - After function: memory must be freed and pointer restored
     */
    
    /*
     * Array Parameter Restoration Loop:
     * For each array parameter (i = 0 to arrCount-1):
     */
    for(int i=0; i< arrCount; i++) {
        
        /*
         * Step 2: Restore the calling context array to its stack-saved reference
         * 
         * WHAT HAPPENS:
         * - methodCallingArrayPlaceHolderAddrs[i] points to argument array in calling context
         * - arrayStackCurrent[i] contains the calling context's array reference from Phase 4
         * 
         * EXAMPLE:
         * When processArray(15) returns to processArray(20):
         * - methodCallingArrayPlaceHolderAddrs[0] points to 'arr' in processArray(20)
         * - arrayStackCurrent[0] contains the array reference from processArray(20)
         * - *methodCallingArrayPlaceHolderAddrs[0] = array_ref (restore outer 'arr')
         * 
         * This ensures proper array reference restoration across recursive calls.
         */
        auto methodArgReturnVal = *methodCalledArrayPlaceHolderAddrs[i];
        *methodCalledArrayPlaceHolderAddrs[i] = methodCalledArrayValue[i];
        *methodCallingArrayPlaceHolderAddrs[i] = methodArgReturnVal;
    }
    //delete[] methodCalledArrayValue;
    



    // Function execution complete - calling context fully restored
}

void BuiltInFunctionsImpl::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    std::list<double *> methodArgVariableAddrList;
    std::list<ArrayValue **> methodArgArrayAddrList;

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

    methodArgVariableAddr = new double *[varCount];
    methodArgArrayAddr = new ArrayValue **[arrCount];

    for (int i = 0; i < varCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }

    for (int i = 0; i < arrCount; i++) {
        methodArgArrayAddr[i] = methodArgArrayAddrList.front();
        methodArgArrayAddrList.pop_front();
    }
}

void NINF::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = -std::numeric_limits<double>::infinity();
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = -std::numeric_limits<double>::infinity();
        }
    }
}

void PINF::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::numeric_limits<double>::infinity();
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = std::numeric_limits<double>::infinity();
        }
    }
}

static std::random_device rd;  // Non-deterministic random seed
static std::mt19937 gen(rd()); // Mersenne Twister engine
static std::uniform_real_distribution<> dis(0.0, 1.0);

void RAND::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = dis(gen);
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = dis(gen);
        }
    }
}

// All variable based built-in methods:
void ABS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::abs(*methodArgVariableAddr[0]);
    }
}

void SIN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sin(*methodArgVariableAddr[0]);
    }
}

void COS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::cos(*methodArgVariableAddr[0]);
    }
}

void TAN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::tan(*methodArgVariableAddr[0]);
    }
}

void ASIN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::asin(*methodArgVariableAddr[0]);
    }
}

void ACOS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::acos(*methodArgVariableAddr[0]);
    }
}

void ATAN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::atan(*methodArgVariableAddr[0]);
    }
}

void FLOOR::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::floor(*methodArgVariableAddr[0]);
    }
}

void CEIL::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::ceil(*methodArgVariableAddr[0]);
    }
}

void EXP::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::exp(*methodArgVariableAddr[0]);
    }
}

void SQRT::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sqrt(*methodArgVariableAddr[0]);
    }
}

void POW::process() {
    if(varCount == 2) {
        *methodArgVariableAddr[0] = std::pow(*methodArgVariableAddr[0], *methodArgVariableAddr[1]);
    }
}