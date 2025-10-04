//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_FUNCTIONCOMMANDRE_H
#define NATIVE_FUNCTIONCOMMANDRE_H

#include "RuleEngineInputUnits.hpp"
#include "FunctionCall.hpp"
#include "CommandRE.h"
#include "dataContainer/array/ArrayValue.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/DataContainerValue.h"
#include "FunctionCallRE.h"
#include "dataContainer/DataContainerValueFunctionCommandRE.h"
#include<unordered_map>
#include <list>

/**
 * FunctionCommandRE class handles function call execution in the rule engine.
 * This class manages the complex process of setting up function parameters,
 * executing function body, and restoring the calling context.
 * 
 * Key responsibilities:
 * - Parameter mapping between calling and called functions
 * - Stack management for variables and arrays
 * - Execution of function body commands
 * - Context restoration after function completion
 * - Memory management for local variables and arrays
 */
class FunctionCommandRE : public RuleEngineInputUnits {
protected:
    // ==================== Core Function Information (Protected for Inheritance) ====================
    
    /**
     * Rule engine representation of the function command (typically unused in current implementation).
     * This could be used for alternative processing paths or additional metadata.
     * Protected to allow derived classes (like built-in functions) to access if needed.
     */
    FunctionCallRE* functionCommandRE = nullptr;
    
    /**
     * Rule engine representation of the function definition (callee side).
     * Contains function body, parameter definitions, local variables, and execution commands.
     * This is the blueprint of the function being called.
     * Protected to allow built-in function classes to access function metadata.
     */
    FunctionCallRE* functionInfoRE = nullptr;
    
    /**
     * Information about the function call being made (caller side).
     * Includes argument names, argument types, calling context, and argument count.
     * This represents the "call site" information.
     * Protected to allow built-in functions to access call information.
     */
    FunctionCall* functionCommandInfo = nullptr;

    // ==================== Parameter Count Information (Protected for Built-ins) ====================
    
    /**
     * Number of variable arguments (non-array) passed to the function.
     * This count is derived during setFields() by examining argument types.
     * Used for loop bounds in parameter setup and restoration phases.
     * Protected so built-in function classes can use it for argument validation.
     */
    int varCount = 0;
    
    /**
     * Number of array arguments passed to the function.
     * This count is derived during setFields() by examining argument types.
     * Used for loop bounds in array parameter setup and restoration phases.
     * Protected so built-in function classes can use it for argument validation.
     */
    int arrCount = 0;
private:
    // ==================== Legacy Code (Commented Out) ====================
    /*
     * The following commented code represents earlier design iterations.
     * Kept for reference and potential future use.
     */
//    DataContainerRE** arguments;
//    DataContainerRE** functionInfoArgs;
//    double ** argsVariableArr;
//    ArrayValue *** argsArrayArr;
//    double ** functionInfoArgsVariableArr;
//    ArrayValue *** functionInfoArgsArrayArr;
//    double *** dataContainerVariableValue;
//    ArrayValue ** dataContainerArrayValue;

    // ==================== Function Execution Information ====================
    
    /**
     * Total number of arguments passed to the function.
     * Set from functionCommandInfo->argumentsSize during initialization.
     * Used for validation and loop bounds in argument processing.
     */
    int argSize = 0;
    
    /**
     * First command to execute in the function body.
     * This is the entry point for function execution and represents the head
     * of the command chain that forms the function's body.
     * Set during setFields() from functionInfoRE->commmandRe or retrieved from map.
     */
    CommandRE* firstCommand;

    CommandRE* command = nullptr;

    // ==================== Total Variable/Array Counts ====================
    
    /**
     * Total number of variables declared within the function (including parameters).
     * This includes both function parameters and local variables declared inside the function.
     * Used for memory allocation and restoration loop bounds.
     * Calculated during setFields() by examining functionInfoRE->allVariablesInMethod.
     */
    int totalVarCount = 0;
    
    /**
     * Total number of arrays declared within the function (including parameters).
     * This includes both function array parameters and local arrays declared inside the function.
     * Used for memory allocation and restoration loop bounds.
     * Calculated during setFields() by examining functionInfoRE->allVariablesInMethod.
     */
    int totalArrCount = 0;
    int totalDataContainerCount = 0;

    DataContainerValueFunctionCommandRE methodArgContainerFinalValue;

    // ==================== Parameter Mapping - DataContainer Arguments ====================
    
    /**
     * Array of pointers to parameter DataContainerValue in the called function.
     * Each element points to the DataContainerValue* of parameters in the function definition.
     * Size: argSize (total arguments including both variables and arrays)
     * 
     * Usage:
     * - During parameter setup: saves original DataContainerValue* references from called function context
     * - During restoration: restored to original DataContainerValue* references for proper stack management
     * 
     * Example: If function is func(a, b), methodCalledOriginalPlaceHolderAddrs[0] points to 'a's DataContainerValue*
     */
    DataContainerValue** methodCalledOriginalPlaceHolderAddrs = nullptr;
    
    /**
     * Array of pointers to argument DataContainerValue addresses in the calling function.
     * Each element points to the DataContainerValue* of arguments being passed to the function.
     * Size: argSize (total arguments including both variables and arrays)
     * 
     * Usage:
     * - Source of DataContainerValue references during parameter setup
     * - Target for DataContainerValue reference restoration during cleanup
     * 
     * Example: If called as func(x, y), methodCallingOriginalPlaceHolderAddrs[0] points to 'x's DataContainerValue*
     */
    DataContainerValue** methodCallingOriginalPlaceHolderAddrs = nullptr;

    // ==================== Local Data Management ====================
    
    /**
     * Array of pointers to array parameter addresses in the called function.
     * Each element points to the ArrayValue** of array parameters in the function definition.
     * Size: arrCount
     * 
     * Usage:
     * - During parameter setup: these array parameters receive references from calling arrays
     * - During restoration: restored to original array references
     * 
     * Memory Structure: ArrayValue*** -> ArrayValue** -> ArrayValue* -> actual array data
     */
   // ArrayValue*** methodCalledArrayPlaceHolderAddrs = nullptr;

    /**
     * Array of pointers to argument array addresses in the calling function.
     * Each element points to the ArrayValue** of arrays being passed as arguments.
     * Size: arrCount
     * 
     * Usage:
     * - Source of array references during parameter setup
     * - Target for array reference restoration during cleanup
     * 
     * Purpose: Enables array parameter passing by reference semantics
     */
//    ArrayValue*** methodCallingArrayPlaceHolderAddrs = nullptr;

    // ==================== Local Variable Management ====================
    /**
     * Array of pointers to all DataContainerValue addresses within the function.
     * Includes both parameters and local data containers declared in the function.
     * Size: totalVarCount + totalArrCount (all data containers in function)
     * 
     * Structure:
     * - Index 0 to argSize-1: Function parameter data container addresses
     * - Index argSize to total-1: Local data container addresses
     * 
     * Usage:
     * - Allows direct access to any data container in function scope
     * - Used during restoration to reset data containers to saved values
     */
    double** methodArgVariableAddr = nullptr;

    // ==================== Local Array Management ====================
    /**
     * Array of pointers to all array addresses within the function.
     * Points to the actual ArrayValue* pointers for both parameters and local arrays.
     * Size: totalArrCount
     * 
     * Memory Structure: double*** -> double** -> double* (actual array data)
     * 
     * Usage:
     * - Direct access to array pointers in function scope
     * - Memory allocation for local arrays (new double[size])
     * - Memory deallocation during cleanup (delete[])
     */
    double*** methodArgArrayAddr = nullptr;
    
    /**
     * Array storing the total size of each array in the function.
     * Used for proper memory allocation of local arrays during function execution.
     * Size: totalArrCount
     * 
     * Purpose:
     * - Index 0 to arrCount-1: Sizes of parameter arrays (for reference)
     * - Index arrCount to totalArrCount-1: Sizes of local arrays (for allocation)
     * 
     * Usage:
     * - During Phase 2: new double[methodArgArrayTotalSize[i]] for local arrays
     * - During Phase 6: Ensures proper memory management
     */
    int* methodArgArrayTotalSize = nullptr;

    DataContainerValue** methodArgDataContainerAddr = nullptr;

    // ==================== Name Mapping for Debugging ====================
    
    /**
     * Maps data container names from calling context to function parameter names.
     * Used primarily for debugging and tracking parameter relationships.
     * 
     * Key: Name of data container (variable/array) in calling context
     * Value: Name of parameter in function definition
     * 
     * Example Mapping:
     * Function definition: func(paramVar, paramArray) { ... }
     * Function call: func(callingVar, callingArray)
     * Map entries: {"callingVar" -> "paramVar", "callingArray" -> "paramArray"}
     * 
     * Usage:
     * - Debugging: Track which calling data containers map to which parameters
     * - Error reporting: Provide meaningful variable names in stack traces
     * - Development: Understand parameter flow in complex recursive calls
     */
    std::unordered_map<std::string, std::string> dataContainerNameMethodMap;

public:
    // ==================== Constructor and Destructor ====================
    
    /**
     * Constructor for FunctionCommandRE.
     * Initializes the function call execution context.
     * 
     * @param functionCommmandInfo Information about the function call being made (caller side)
     *                            Contains argument list, argument types, and calling context
     * @param functionInfo Rule engine representation of the function definition (callee side)
     *                    Contains function body, parameters, and local variable definitions
     */
    FunctionCommandRE(FunctionCall* functionCommmandInfo, FunctionCallRE* functionInfo);
    
    /**
     * Destructor - cleans up dynamically allocated memory.
     * Safely deletes core function information objects with null pointer checks.
     * 
     * Note: Other dynamically allocated arrays (address mappings, variable storage)
     * are cleaned up elsewhere in the lifecycle to avoid double deletion issues
     * and maintain proper object lifetime management.
     */
    void destroy() override {
        // Clean up core function information objects with null checks
        if(functionCommandInfo != nullptr) {
            delete functionCommandInfo;
        }
        if(functionCommandRE != nullptr) {
            delete functionCommandRE;
        }
        if(functionInfoRE != nullptr) {
            delete functionInfoRE;
        }
        
        // Legacy cleanup code kept for reference
        // These arrays are managed differently to prevent double deletion
    }
    
    // ==================== Core Interface Methods ====================
    
    /**
     * Sets up all field mappings and initializes data structures.
     * This method performs the complex task of:
     * 1. Mapping function parameters to calling arguments
     * 2. Separating variables from arrays in argument lists
     * 3. Setting up address mappings for parameter passing
     * 4. Initializing local variable and array storage
     * 5. Building name mapping for debugging purposes
     * 
     * @param map Global map containing all rule engine objects indexed by their IDs
     *           Used to resolve references between function calls and definitions
     */
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map);
    
    /**
     * Main execution method for the function call.
     * Orchestrates the complete function call lifecycle through 6 phases:
     * 
     * Phase 1: Parameter setup and variable context saving
     * Phase 2: Array parameter setup and local array allocation  
     * Phase 3: Function body execution
     * Phase 4: Context restoration preparation
     * Phase 5: Variable restoration
     * Phase 6: Array restoration and cleanup
     * 
     * This method handles complex stack management required for proper function
     * call semantics including recursive calls and memory management.
     */
    void process();
    
    /**
     * Alternative processing method (currently unused).
     * Placeholder for potential future method-specific processing logic
     * or specialized execution paths for different function types.
     */
    void processMethod();

};

// ==================== Built-in Function System ====================

/**
 * Enumeration of all supported built-in functions.
 * These are predefined functions with optimized implementations
 * that don't require full function call overhead.
 */
enum BuiltInFunctions {
    NINF,  // Negative infinity assignment
    PINF,  // Positive infinity assignment  
    RAND,  // Random number generation
    ABS,   // Absolute value
    SIN,   // Sine trigonometric function
    COS,   // Cosine trigonometric function
    TAN,   // Tangent trigonometric function
    ASIN,  // Arcsine trigonometric function
    ACOS,  // Arccosine trigonometric function
    ATAN,  // Arctangent trigonometric function
    FLOOR, // Floor function (round down)
    CEIL,  // Ceiling function (round up)
    EXP,   // Exponential function (e^x)
};

/**
 * Base class for all built-in function implementations.
 * Provides simplified parameter handling for built-in functions that don't
 * require the full complexity of user-defined function calls.
 * 
 * Key differences from FunctionCommandRE:
 * - No function body execution
 * - No local variables or arrays
 * - Simplified parameter mapping
 * - Direct computation on arguments
 */
class BuiltInFunctionsImpl : public FunctionCommandRE {
protected:
    // ==================== Simplified Parameter Access ====================
    
    /**
     * Direct access to DataContainerValue pointers for arguments passed to built-in functions.
     * Simplified version of parameter handling for built-in functions.
     * Size: argSize (total number of arguments passed to function)
     * 
     * Usage: Allows built-in functions to directly access and modify argument data containers
     * without the overhead of full function call parameter mapping and stack management.
     * This unified approach handles both variable and array arguments through the same interface.
     */
    DataContainerValue** methodArgDataContainerAddr = nullptr;
public:
    // ==================== Built-in Function Constructor ====================
    
    /**
     * Constructor for built-in function implementations.
     * Initializes with function call information but no function definition,
     * since built-in functions have hardcoded implementations.
     * 
     * @param pCall Function call information containing arguments and calling context
     */
    BuiltInFunctionsImpl(FunctionCall *pCall) : FunctionCommandRE(pCall, nullptr) {}

    // ==================== Memory Management ====================
    
    /**
     * Destructor for built-in function implementations.
     * Cleans up simplified parameter arrays used by built-in functions.
     * Note: Much simpler than base class since no complex stack management needed.
     */
    void destroy() override {
        if(methodArgDataContainerAddr != nullptr) {
            delete[] methodArgDataContainerAddr;
        }
    }

    // ==================== Simplified Setup Interface ====================
    
    /**
     * Simplified field setup for built-in functions.
     * Sets up direct access to arguments without complex parameter mapping
     * since built-in functions don't have function bodies or local variables.
     * 
     * @param map Global map containing rule engine objects (used to resolve arguments)
     */
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;
};

// ==================== Individual Built-in Function Classes ====================

/**
 * Negative Infinity Assignment Function.
 * Sets variable or array elements to negative infinity (-∞).
 * 
 * Usage:
 * - NINF(variable) - sets variable to -∞
 * - NINF(array) - sets all array elements to -∞
 */
class NINF : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for negative infinity function.
     * @param pCall1 Function call information with target variable/array
     */
    NINF(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

    /**
     * Executes negative infinity assignment.
     * Sets the target variable or all array elements to -std::numeric_limits<double>::infinity()
     */
    void process() override;
};

/**
 * Positive Infinity Assignment Function.
 * Sets variable or array elements to positive infinity (+∞).
 * 
 * Usage:
 * - PINF(variable) - sets variable to +∞
 * - PINF(array) - sets all array elements to +∞
 */
class PINF : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for positive infinity function.
     * @param pCall1 Function call information with target variable/array
     */
    PINF(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes positive infinity assignment.
     * Sets the target variable or all array elements to +std::numeric_limits<double>::infinity()
     */
    void process() override;
};

/**
 * Random Number Generation Function.
 * Generates random numbers using Mersenne Twister algorithm.
 * 
 * Usage:
 * - RAND(variable) - sets variable to random value [0.0, 1.0)
 * - RAND(array) - sets all array elements to random values [0.0, 1.0)
 */
class RAND : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for random number generation function.
     * @param pCall1 Function call information with target variable/array
     */
    RAND(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes random number generation.
     * Uses static random engine to generate uniformly distributed random numbers.
     */
    void process() override;
};

/**
 * Absolute Value Function.
 * Computes the absolute value of numeric arguments.
 * 
 * Usage:
 * - ABS(variable) - sets variable to |variable|
 * - Currently only supports single variable arguments
 */
class ABS : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for absolute value function.
     * @param pCall1 Function call information with numeric variable
     */
    ABS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes absolute value computation.
     * Modifies the input variable to contain its absolute value.
     */
    void process() override;
};

/**
 * Sine Trigonometric Function.
 * Computes the sine of the input angle (in radians).
 * 
 * Usage:
 * - SIN(variable) - sets variable to sin(variable)
 */
class SIN : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for sine function.
     * @param pCall1 Function call information with angle variable (radians)
     */
    SIN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes sine computation.
     * Modifies the input variable to contain sin(variable).
     */
    void process() override;
};

/**
 * Cosine Trigonometric Function.
 * Computes the cosine of the input angle (in radians).
 * 
 * Usage:
 * - COS(variable) - sets variable to cos(variable)
 */
class COS : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for cosine function.
     * @param pCall1 Function call information with angle variable (radians)
     */
    COS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes cosine computation.
     * Modifies the input variable to contain cos(variable).
     */
    void process() override;
};

/**
 * Tangent Trigonometric Function.
 * Computes the tangent of the input angle (in radians).
 * 
 * Usage:
 * - TAN(variable) - sets variable to tan(variable)
 */
class TAN : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for tangent function.
     * @param pCall1 Function call information with angle variable (radians)
     */
    TAN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes tangent computation.
     * Modifies the input variable to contain tan(variable).
     */
    void process() override;
};

/**
 * Arcsine Trigonometric Function.
 * Computes the arcsine (inverse sine) of the input value.
 * 
 * Usage:
 * - ASIN(variable) - sets variable to asin(variable)
 * Input domain: [-1, 1], Output range: [-π/2, π/2]
 */
class ASIN : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for arcsine function.
     * @param pCall1 Function call information with input variable
     */
    ASIN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes arcsine computation.
     * Modifies the input variable to contain asin(variable).
     */
    void process() override;
};

/**
 * Arccosine Trigonometric Function.
 * Computes the arccosine (inverse cosine) of the input value.
 * 
 * Usage:
 * - ACOS(variable) - sets variable to acos(variable)
 * Input domain: [-1, 1], Output range: [0, π]
 */
class ACOS : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for arccosine function.
     * @param pCall1 Function call information with input variable
     */
    ACOS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes arccosine computation.
     * Modifies the input variable to contain acos(variable).
     */
    void process() override;
};

/**
 * Arctangent Trigonometric Function.
 * Computes the arctangent (inverse tangent) of the input value.
 * 
 * Usage:
 * - ATAN(variable) - sets variable to atan(variable)
 * Input domain: (-∞, ∞), Output range: (-π/2, π/2)
 */
class ATAN : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for arctangent function.
     * @param pCall1 Function call information with input variable
     */
    ATAN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes arctangent computation.
     * Modifies the input variable to contain atan(variable).
     */
    void process() override;
};

/**
 * Floor Function.
 * Computes the largest integer less than or equal to the input value.
 * 
 * Usage:
 * - FLOOR(variable) - sets variable to floor(variable)
 * Example: FLOOR(3.7) = 3.0, FLOOR(-2.3) = -3.0
 */
class FLOOR : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for floor function.
     * @param pCall1 Function call information with input variable
     */
    FLOOR(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes floor computation.
     * Modifies the input variable to contain floor(variable).
     */
    void process() override;
};

/**
 * Ceiling Function.
 * Computes the smallest integer greater than or equal to the input value.
 * 
 * Usage:
 * - CEIL(variable) - sets variable to ceil(variable)
 * Example: CEIL(3.2) = 4.0, CEIL(-2.8) = -2.0
 */
class CEIL : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for ceiling function.
     * @param pCall1 Function call information with input variable
     */
    CEIL(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes ceiling computation.
     * Modifies the input variable to contain ceil(variable).
     */
    void process() override;
};

/**
 * Exponential Function.
 * Computes e raised to the power of the input value.
 * 
 * Usage:
 * - EXP(variable) - sets variable to e^variable
 * Where e ≈ 2.71828 (Euler's number)
 */
class EXP : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for exponential function.
     * @param pCall1 Function call information with exponent variable
     */
    EXP(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes exponential computation.
     * Modifies the input variable to contain exp(variable).
     */
    void process() override;
};

/**
 * Square Root Function.
 * Computes the positive square root of the input value.
 * 
 * Usage:
 * - SQRT(variable) - sets variable to √variable
 * Input domain: [0, ∞), undefined for negative values
 */
class SQRT : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for square root function.
     * @param pCall1 Function call information with input variable
     */
    SQRT(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes square root computation.
     * Modifies the input variable to contain sqrt(variable).
     */
    void process() override;
};

/**
 * Power Function.
 * Computes the first argument raised to the power of the second argument.
 * 
 * Usage:
 * - POW(base, exponent) - sets base to base^exponent
 * Requires exactly 2 variable arguments
 */
class POW : public BuiltInFunctionsImpl {
public:
    /**
     * Constructor for power function.
     * @param pCall1 Function call information with base and exponent variables
     */
    POW(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    
    /**
     * Executes power computation.
     * Modifies the first variable to contain pow(first_var, second_var).
     */
    void process() override;
};

// ==================== Factory Function for Function Command Creation ====================

/**
 * Factory function to create appropriate FunctionCommandRE objects.
 * Determines whether to create a built-in function implementation or a regular function call
 * based on the function ID string.
 * 
 * This function implements the Factory Pattern to encapsulate object creation logic
 * and provide a single entry point for creating function command objects.
 * 
 * @param functionCommand Information about the function call (arguments, context)
 * @param id String identifier for the function being called
 * @param map Global map of rule engine objects for resolving function definitions
 * 
 * @return FunctionCommandRE* Pointer to appropriate function command object:
 *         - Built-in function implementation (NINF, PINF, ABS, etc.) for predefined functions
 *         - Regular FunctionCommandRE for user-defined functions
 * 
 * Built-in Functions Supported:
 * - NINF, PINF: Infinity assignment functions
 * - RAND: Random number generation
 * - ABS: Absolute value
 * - SIN, COS, TAN: Basic trigonometric functions
 * - ASIN, ACOS, ATAN: Inverse trigonometric functions  
 * - FLOOR, CEIL: Rounding functions
 * - EXP: Exponential function
 * - SQRT: Square root function
 * - POW: Power function
 * 
 * Usage Example:
 * ```cpp
 * std::string funcId = "SIN";
 * FunctionCommandRE* cmd = GetFunctionCommandRE(callInfo, funcId, objectMap);
 * // Returns a SIN object for built-in sine function
 * 
 * std::string userFuncId = "myCustomFunction";  
 * FunctionCommandRE* userCmd = GetFunctionCommandRE(callInfo, userFuncId, objectMap);
 * // Returns a FunctionCommandRE object for user-defined function
 * ```
 */
static FunctionCommandRE* GetFunctionCommandRE(FunctionCall* functionCommand, std::string& id, std::unordered_map<std::string, RuleEngineInputUnits *> *map)
{
    // Check against all built-in function identifiers
    // Each built-in function has its own optimized implementation
    if(id == "NINF") {
        return new class NINF(functionCommand);
    } else if(id == "PINF") {
        return new class PINF(functionCommand);
    } else if(id == "RAND") {
        return new class RAND(functionCommand);
    } else if(id == "ABS") {
        return new class ABS(functionCommand);
    } else if(id == "SIN") {
        return new class SIN(functionCommand);
    } else if(id == "COS") {
        return new class COS(functionCommand);
    } else if(id == "TAN") {
        return new class TAN(functionCommand);
    } else if(id == "ASIN") {
        return new class ASIN(functionCommand);
    } else if(id == "ACOS") {
        return new class ACOS(functionCommand);
    } else if(id == "ATAN") {
        return new class ATAN(functionCommand);
    } else if(id == "FLOOR") {
        return new class FLOOR(functionCommand);
    } else if(id == "CEIL") {
        return new class CEIL(functionCommand);
    } else if(id == "EXP") {
        return new class EXP(functionCommand);
    } else if(id == "SQRT") {
        return new class SQRT(functionCommand);
    } else if(id == "POW") {
        return new class POW(functionCommand);
    }

    // Default case: Create regular function call for user-defined functions
    // Retrieves function definition from the global object map
    return new FunctionCommandRE(functionCommand, (FunctionCallRE *) map->at(functionCommand->id));
}

#endif //NATIVE_FUNCTIONCOMMANDRE_H
