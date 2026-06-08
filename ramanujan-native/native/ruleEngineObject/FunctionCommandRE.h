//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_FUNCTIONCOMMANDRE_H
#define NATIVE_FUNCTIONCOMMANDRE_H

#include "CommandRE.h"
#include "DataContainerValueFunctionCommandREMemMaintainer.h"
#include "FunctionCall.hpp"
#include "FunctionCallRE.h"
#include "RuleEngineInputUnits.hpp"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/DataContainerValue.h"
#include "dataContainer/DataContainerValueFunctionCommandRE.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/array/ArrayValue.h"
#include <list>
#include <unordered_map>
#include <vector>
#ifdef GPU_ENABLED
#ifdef __APPLE__
#include <OpenCL/cl.h>
#else
// On Android we dlopen libOpenCL.so at runtime (see native/opencl_loader.h).
// Include the Khronos headers first for the types, then let opencl_loader.h
// redefine every cl* to its pfn_* pointer.  This must happen here in the .h
// (not only in the .cpp) because destroy() calls clReleaseKernel /
// clReleaseMemObject directly and those calls are emitted from this header.
#include <CL/cl.h>
#ifdef __ANDROID__
#include "../opencl_loader.h"
#endif
#endif
#endif

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
public:
  static thread_local bool hasEncounteredReturn;

protected:
  // ==================== Core Function Information (Protected for Inheritance)
  // ====================

  /**
   * Rule engine representation of the function command (typically unused in
   * current implementation). This could be used for alternative processing
   * paths or additional metadata. Protected to allow derived classes (like
   * built-in functions) to access if needed.
   */
  FunctionCallRE *functionCommandRE = nullptr;

  /**
   * Rule engine representation of the function definition (callee side).
   * Contains function body, parameter definitions, local variables, and
   * execution commands. This is the blueprint of the function being called.
   * Protected to allow built-in function classes to access function metadata.
   */
  FunctionCallRE *functionInfoRE = nullptr;

  /**
   * Information about the function call being made (caller side).
   * Includes argument names, argument types, calling context, and argument
   * count. This represents the "call site" information. Protected to allow
   * built-in functions to access call information.
   */
  FunctionCall *functionCommandInfo = nullptr;

  // ==================== Parameter Count Information (Protected for Built-ins)
  // ====================

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

protected:
  // ==================== Function Execution Information ====================

  static const int maxArgSize = 255;

  /**
   * Total number of arguments passed to the function.
   * Set from functionCommandInfo->argumentsSize during initialization.
   */
  int argSize = 0;

  /**
   * Array of pointers to argument DataContainerValue addresses in the calling
   * function. Each element points to the DataContainerValue* of arguments being
   * passed to the function. Size: argSize (total arguments including both
   * variables and arrays)
   */
  DataContainerValue *methodCallingOriginalPlaceHolderAddrs[maxArgSize];

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

  /**
   * First unit to execute in the function body.
   */
  RuleEngineInputUnits *firstUnit;

  RuleEngineInputUnits *unit = nullptr;

  int totalVarCount = 0;
  int totalArrCount = 0;
  int totalDataContainerCount = 0;

  DataContainerValueFunctionCommandRE **currentAsk;

  /**
   * Memory maintainer for efficient allocation and deallocation of
   * DataContainerValueFunctionCommandRE objects. This is injected from the
   * Processor and shared across all function calls in a processing session.
   */
  DataContainerValueFunctionCommandREMemMaintainer *memMaintainer = nullptr;

  /**
   * Array of pointers to parameter DataContainerValue in the called function.
   */
  DataContainerValue *methodCalledOriginalPlaceHolderAddrs[maxArgSize];

  /**
   * Array of pointers to all DataContainerValue addresses within the function.
   * Includes both parameters and local data containers declared in the
   * function.
   */
  DataContainerValue *methodArgDataContainerAddr[maxArgSize];

  std::unordered_map<std::string, std::string> dataContainerNameMethodMap;

public:
  // ==================== Constructor and Destructor ====================

  /**
   * Constructor for FunctionCommandRE.
   * Initializes the function call execution context.
   *
   * @param functionCommmandInfo Information about the function call being made
   * (caller side) Contains argument list, argument types, and calling context
   * @param functionInfo Rule engine representation of the function definition
   * (callee side) Contains function body, parameters, and local variable
   * definitions
   */
  FunctionCommandRE(FunctionCall *functionCommmandInfo,
                    FunctionCallRE *functionInfo);

  /**
   * Destructor - cleans up dynamically allocated memory.
   * Safely deletes core function information objects with null pointer checks.
   *
   * Note: Other dynamically allocated arrays (address mappings, variable
   * storage) are cleaned up elsewhere in the lifecycle to avoid double deletion
   * issues and maintain proper object lifetime management.
   */
  void destroy() override {
    if (functionCommandInfo != nullptr) {
      delete functionCommandInfo;
    }
    if (functionCommandRE != nullptr) {
      delete functionCommandRE;
    }
    if (functionInfoRE != nullptr) {
      delete functionInfoRE;
    }
  }

  // ==================== Core Interface Methods ====================

  /**
   * Sets the memory maintainer for efficient allocation/deallocation.
   * Must be called before setFields() to enable memory pool usage.
   *
   * @param maintainer Pointer to the memory maintainer instance
   */
  void setMemMaintainer(
      DataContainerValueFunctionCommandREMemMaintainer *maintainer) {
    this->memMaintainer = maintainer;
  }

  /**
   * Sets up all field mappings and initializes data structures.
   * This method performs the complex task of:
   * 1. Mapping function parameters to calling arguments
   * 2. Separating variables from arrays in argument lists
   * 3. Setting up address mappings for parameter passing
   * 4. Initializing local variable and array storage
   * 5. Building name mapping for debugging purposes
   *
   * @param map Global map containing all rule engine objects indexed by their
   * IDs Used to resolve references between function calls and definitions
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
   *
   * Returns: nextUnit after function completes (nullptr handled internally)
   */
  RuleEngineInputUnits *process() override;

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
  NINF,         // Negative infinity assignment
  PINF,         // Positive infinity assignment
  RAND,         // Random number generation
  ABS,          // Absolute value
  SIN,          // Sine trigonometric function
  COS,          // Cosine trigonometric function
  TAN,          // Tangent trigonometric function
  ASIN,         // Arcsine trigonometric function
  ACOS,         // Arccosine trigonometric function
  ATAN,         // Arctangent trigonometric function
  FLOOR,        // Floor function (round down)
  CEIL,         // Ceiling function (round up)
  EXP,          // Exponential function (e^x)
  GPU_SYNC,     // GPU explicit sync (GPU → CPU)
  GPU_LOAD,     // GPU explicit load (CPU → GPU)
  RETURN_ARRAYS_ENUM, // Selective array return
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
   * Direct access to DataContainerValue pointers for arguments passed to
   * built-in functions. Simplified version of parameter handling for built-in
   * functions. Size: argSize (total number of arguments passed to function)
   *
   * Usage: Allows built-in functions to directly access and modify argument
   * data containers without the overhead of full function call parameter
   * mapping and stack management. This unified approach handles both variable
   * and array arguments through the same interface.
   */
  DataContainerValue **methodArgDataContainerAddr = nullptr;

public:
  // ==================== Built-in Function Constructor ====================

  /**
   * Constructor for built-in function implementations.
   * Initializes with function call information but no function definition,
   * since built-in functions have hardcoded implementations.
   *
   * @param pCall Function call information containing arguments and calling
   * context
   */
  BuiltInFunctionsImpl(FunctionCall *pCall)
      : FunctionCommandRE(pCall, nullptr) {}

  // ==================== Memory Management ====================

  /**
   * Destructor for built-in function implementations.
   * Cleans up simplified parameter arrays used by built-in functions.
   * Note: Much simpler than base class since no complex stack management
   * needed.
   */
  void destroy() override {
    if (methodArgDataContainerAddr != nullptr) {
      delete[] methodArgDataContainerAddr;
    }
  }

  // ==================== Simplified Setup Interface ====================

  /**
   * Simplified field setup for built-in functions.
   * Sets up direct access to arguments without complex parameter mapping
   * since built-in functions don't have function bodies or local variables.
   *
   * @param map Global map containing rule engine objects (used to resolve
   * arguments)
   */
  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;
};

// ==================== Individual Built-in Function Classes
// ====================

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
   * Sets the target variable or all array elements to
   * -std::numeric_limits<double>::infinity()
   */
  RuleEngineInputUnits *process() override;
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
   * Sets the target variable or all array elements to
   * +std::numeric_limits<double>::infinity()
   */
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
};

/**
 * Natural Logarithm Function.
 * Computes the natural logarithm (base e) of the input value.
 *
 * Usage:
 * - LOG(variable) - sets variable to ln(variable)
 * Input domain: (0, ∞), undefined (NaN) for non-positive values
 */
class LOG : public BuiltInFunctionsImpl {
public:
  /**
   * Constructor for natural logarithm function.
   * @param pCall1 Function call information with input variable
   */
  LOG(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

  /**
   * Executes natural logarithm computation.
   * Modifies the input variable to contain ln(variable).
   */
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
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
  RuleEngineInputUnits *process() override;
};

/**
 * GPU Synchronization Function.
 * Reads the GPU buffer back to the host CPU synchronously.
 *
 * Usage:
 * - GPU_SYNC(array) - Explicitly reads the modified array from GPU to CPU.
 *
 * GPU_SYNC overrides setFields() itself so it can hold a direct pointer to
 * the ArrayRE argument, bypassing the BuiltInFunctionsImpl scalar-math
 * DataContainerValue machinery which is not needed here.
 */
class GPU_SYNC : public BuiltInFunctionsImpl {
  ArrayRE *targetArray = nullptr; // resolved in setFields, used in process
public:
  /**
   * Constructor for GPU synchronization function.
   * @param pCall1 Function call information with array variable
   */
  GPU_SYNC(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

  /**
   * Resolve the array argument directly from the global map.
   * We do NOT call BuiltInFunctionsImpl::setFields here — that path is for
   * scalar math built-ins that operate on DataContainerValue pointers.
   */
  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
    if (functionCommandInfo->argumentsSize >= 1) {
      targetArray =
          dynamic_cast<ArrayRE *>(map->at(functionCommandInfo->arguments[0]));
    }
  }

  /**
   * Executes GPU synchronization.
   * Reads back the array data via OpenCL.
   */
  RuleEngineInputUnits *process() override;
};

/**
 * GPU Load Function (CPU → GPU).
 * Writes the host array data to the GPU buffer synchronously.
 *
 * Usage:
 * - GPU_LOAD(array) - Uploads the host-side array to GPU memory.
 *
 * This is the inverse of GPU_SYNC. Use it when the host has modified an array
 * (e.g. l_idx_arr[0] = l_idx) and the next GPU kernel needs the new value.
 */
class GPU_LOAD : public BuiltInFunctionsImpl {
  ArrayRE *targetArray = nullptr;
public:
  GPU_LOAD(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
    if (functionCommandInfo->argumentsSize >= 1) {
      targetArray =
          dynamic_cast<ArrayRE *>(map->at(functionCommandInfo->arguments[0]));
    }
  }

  RuleEngineInputUnits *process() override;
};

/**
 * Selective Array Return Directive.
 * Marks specified arrays so that Processor::arrChangeMap() returns only those
 * arrays instead of all modified arrays.
 *
 * Usage:
 * - RETURN(arr1, arr2, ...) - marks arr1, arr2, ... for selective return
 *
 * If RETURN() is never called, arrChangeMap() returns all modified arrays
 * (unchanged behaviour). If called, only the marked arrays are returned.
 */
class RETURN_ARRAYS : public BuiltInFunctionsImpl {
  std::vector<ArrayRE *> targetArrays;
public:
  RETURN_ARRAYS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
      ArrayRE *arr = dynamic_cast<ArrayRE *>(
          map->at(functionCommandInfo->arguments[i]));
      if (arr != nullptr) {
        targetArrays.push_back(arr);
      }
    }
  }

  RuleEngineInputUnits *process() override {
    for (ArrayRE *arr : targetArrays) {
      arr->markedForReturn = true;
    }
    return nextUnit;
  }
};

#ifdef GPU_ENABLED
// ==================== GPU Function Command ====================

/**
 * Subclass of FunctionCommandRE for functions flagged as GPU kernels.
 * process() dispatches the OpenCL kernel, then delegates to
 * FunctionCommandRE::process() for the standard calling-convention wrap-up.
 */
class GPUFunctionCommandRE : public FunctionCommandRE {
  std::vector<int> gpuDataArgIndices;
  cl_program gpuProgram = nullptr;
  cl_kernel gpuKernel = nullptr;
  cl_mem gpuBuffers[maxArgSize] = {};
  size_t gpuBufferSizes[maxArgSize] = {};

  // GPU parallelism configuration (computed once in setFields, reused in
  // process)
  std::vector<int> gpuParallelismIdxs;
  cl_uint gpuWorkDim = 0;
  size_t gpuGlobalWorkSize[3] = {}; // OpenCL supports up to 3 dimensions

  int gpuDataArgCount = 0;
  ArrayValue *gpuAvCache[maxArgSize] = {};

  // process() working state (fields to avoid stack allocation on each call)
  cl_int gpuErr = CL_SUCCESS;
  bool gpuBufferError = false;
  bool gpuZeroWorkSize = false;
  bool gpuBufferReallocated = false;
  size_t gpuNeeded = 0;
  cl_int gpuSetErr = CL_SUCCESS;

public:
  GPUFunctionCommandRE(FunctionCall *functionCommand,
                       FunctionCallRE *functionInfo)
      : FunctionCommandRE(functionCommand, functionInfo) {}

  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;

  void destroy() override {
    FunctionCommandRE::destroy();
    if (gpuKernel) {
      clReleaseKernel(gpuKernel);
      gpuKernel = nullptr;
    }
    for (int _i = 0; _i < maxArgSize; _i++) {
      if (gpuBuffers[_i]) {
        clReleaseMemObject(gpuBuffers[_i]);
        gpuBuffers[_i] = nullptr;
      }
    }
  }

  RuleEngineInputUnits *process() override;
};
#endif // GPU_ENABLED

// ==================== Factory Function for Function Command Creation
// ====================

/**
 * Factory function to create appropriate FunctionCommandRE objects.
 * Determines whether to create a built-in function implementation or a regular
 * function call based on the function ID string.
 *
 * This function implements the Factory Pattern to encapsulate object creation
 * logic and provide a single entry point for creating function command objects.
 *
 * @param functionCommand Information about the function call (arguments,
 * context)
 * @param id String identifier for the function being called
 * @param map Global map of rule engine objects for resolving function
 * definitions
 *
 * @return FunctionCommandRE* Pointer to appropriate function command object:
 *         - Built-in function implementation (NINF, PINF, ABS, etc.) for
 * predefined functions
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
 * FunctionCommandRE* userCmd = GetFunctionCommandRE(callInfo, userFuncId,
 * objectMap);
 * // Returns a FunctionCommandRE object for user-defined function
 * ```
 */
static FunctionCommandRE *GetFunctionCommandRE(
    FunctionCall *functionCommand, std::string &id,
    std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
  // Check against all built-in function identifiers
  // Each built-in function has its own optimized implementation
  if (id == "NINF") {
    return new class NINF(functionCommand);
  } else if (id == "PINF") {
    return new class PINF(functionCommand);
  } else if (id == "RAND") {
    return new class RAND(functionCommand);
  } else if (id == "ABS") {
    return new class ABS(functionCommand);
  } else if (id == "SIN") {
    return new class SIN(functionCommand);
  } else if (id == "COS") {
    return new class COS(functionCommand);
  } else if (id == "TAN") {
    return new class TAN(functionCommand);
  } else if (id == "ASIN") {
    return new class ASIN(functionCommand);
  } else if (id == "ACOS") {
    return new class ACOS(functionCommand);
  } else if (id == "ATAN") {
    return new class ATAN(functionCommand);
  } else if (id == "FLOOR") {
    return new class FLOOR(functionCommand);
  } else if (id == "CEIL") {
    return new class CEIL(functionCommand);
  } else if (id == "EXP") {
    return new class EXP(functionCommand);
  } else if (id == "LOG") {
    return new class LOG(functionCommand);
  } else if (id == "SQRT") {
    return new class SQRT(functionCommand);
  } else if (id == "POW") {
    return new class POW(functionCommand);
  } else if (id == "GPU_SYNC") {
    return new class GPU_SYNC(functionCommand);
  } else if (id == "GPU_LOAD") {
    return new class GPU_LOAD(functionCommand);
  } else if (id == "RETURN") {
    return new RETURN_ARRAYS(functionCommand);
  }

  // Default case: user-defined functions.
  FunctionCallRE *funcInfoRE = (FunctionCallRE *)map->at(functionCommand->id);
#ifdef GPU_ENABLED
  if (funcInfoRE && funcInfoRE->functionCall->isGpu) {
    return new GPUFunctionCommandRE(functionCommand, funcInfoRE);
  }
#endif
  return new FunctionCommandRE(functionCommand, funcInfoRE);
}

#endif // NATIVE_FUNCTIONCOMMANDRE_H
