//
// Created by pranav on 28/3/24.
//

#include "FunctionCommandRE.h"
#include "DataContainerValueFunctionCommandREMemMaintainer.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/array/ArrayValue.h"
#include "DebugPoint.h"
#include "dataContainer/DataContainerValueFunctionCommandRE.h"
#include <vector>

#include <limits>
#include <random>

#ifdef GPU_ENABLED
#include <set>
#include <mutex>

// ==================== OpenCL ====================
// On Android we must dlopen libOpenCL.so at runtime; the Khronos ICD loader
// cannot discover vendor libraries via /etc/OpenCL/vendors/*.icd on Android.
// opencl_loader.h does the dlopen and re-#defines all cl* to pfn_* pointers.
// On all other platforms it is a no-op and we link against the system OpenCL.
#ifdef __APPLE__
#  include <OpenCL/cl.h>
// opencl_loader.h is included by FunctionCommandRE.h on non-Apple/Android builds.
#endif

// ---------------------------------------------------------------------------
// Singleton OpenCL context  (created once, reused for all GPU dispatches)
// cl_context and cl_device_id are thread-safe to share across threads.
// cl_command_queue is shared (s_clCtx.queue), created once alongside the context.
// The shared context is initialised once under GpuContext::initMutex.
// ---------------------------------------------------------------------------
namespace {

static std::string gpuInfoString(cl_platform_id platformId, cl_platform_info infoKey) {
    size_t valueSize = 0;
    cl_int err = clGetPlatformInfo(platformId, infoKey, 0, nullptr, &valueSize);
    if (err != CL_SUCCESS || valueSize == 0) return "unknown";
    std::string value(valueSize, '\0');
    err = clGetPlatformInfo(platformId, infoKey, valueSize, &value[0], nullptr);
    if (err != CL_SUCCESS) return "unknown";
    while (!value.empty() && (value.back() == '\0' || value.back() == '\n' || value.back() == '\r')) value.pop_back();
    return value;
}

static std::string gpuInfoString(cl_device_id deviceId, cl_device_info infoKey) {
    size_t valueSize = 0;
    cl_int err = clGetDeviceInfo(deviceId, infoKey, 0, nullptr, &valueSize);
    if (err != CL_SUCCESS || valueSize == 0) return "unknown";
    std::string value(valueSize, '\0');
    err = clGetDeviceInfo(deviceId, infoKey, valueSize, &value[0], nullptr);
    if (err != CL_SUCCESS) return "unknown";
    while (!value.empty() && (value.back() == '\0' || value.back() == '\n' || value.back() == '\r')) value.pop_back();
    return value;
}

struct GpuContext {
    cl_platform_id    platform    = nullptr;
    cl_device_id      device      = nullptr;
    cl_context        context     = nullptr;
    cl_command_queue  queue       = nullptr;
    bool initialized = false;
    bool available   = false;
    std::mutex        initMutex;   // guards one-time initialisation only

    void init() {
        std::lock_guard<std::mutex> lk(initMutex);
        if (initialized) return;
        initialized = true;

#ifdef __ANDROID__
        // On Android the Khronos ICD loader cannot find /etc/OpenCL/vendors/*.icd
        // files (they don't exist). Load libOpenCL.so via dlopen first so that
        // all subsequent cl* calls resolve to the vendor implementation.
        if (!openclLoad()) {
            fprintf(stderr, "[GPU] Android: could not dlopen libOpenCL.so – "
                            "GPU execution unavailable.\n"
                            "  Ensure AndroidManifest.xml contains:\n"
                            "  <uses-native-library android:name=\"libOpenCL.so\" "
                            "android:required=\"false\"/>\n");
            return;
        }
#endif

        cl_int err;

        // Step 1: count available platforms before fetching them (safer two-step query)
        cl_uint numPlatforms = 0;
        err = clGetPlatformIDs(0, nullptr, &numPlatforms);
        if (err != CL_SUCCESS || numPlatforms == 0) {
            fprintf(stderr, "[GPU] clGetPlatformIDs count query failed "
                            "(err=%d, numPlatforms=%u).\n"
                            "  macOS: ensure the binary links -framework OpenCL, not the Khronos ICD loader.\n"
                            "  Linux: sudo apt install ocl-icd-opencl-dev && install GPU vendor drivers.\n",
                            err, numPlatforms);
            return;
        }

        // Step 2: retrieve the first platform
        err = clGetPlatformIDs(1, &platform, nullptr);
        if (err != CL_SUCCESS) {
            fprintf(stderr, "[GPU] clGetPlatformIDs retrieve failed: err=%d\n", err);
            return;
        }

        // Prefer a GPU device; fall back to any device (e.g. CPU OpenCL on Linux)
        err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, nullptr);
        if (err != CL_SUCCESS) {
            fprintf(stderr, "[GPU] No GPU device found (err=%d), falling back to CL_DEVICE_TYPE_ALL\n", err);
            err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 1, &device, nullptr);
        }
        if (err != CL_SUCCESS) {
            fprintf(stderr, "[GPU] clGetDeviceIDs failed: err=%d\n", err);
            return;
        }

        context = clCreateContext(nullptr, 1, &device, nullptr, nullptr, &err);
        if (err != CL_SUCCESS) {
            fprintf(stderr, "[GPU] clCreateContext failed: err=%d\n", err);
            return;
        }

        // Create a single shared command queue (created once, reused for all kernel dispatches)
#if defined(CL_VERSION_2_0)
        const cl_queue_properties qprops[] = {0};
        queue = clCreateCommandQueueWithProperties(context, device, qprops, &err);
#else
#   ifdef __APPLE__
#       pragma clang diagnostic push
#       pragma clang diagnostic ignored "-Wdeprecated-declarations"
#   endif
        queue = clCreateCommandQueue(context, device, 0, &err);
#   ifdef __APPLE__
#       pragma clang diagnostic pop
#   endif
#endif
        if (err != CL_SUCCESS) {
            fprintf(stderr, "[GPU] clCreateCommandQueue failed: %d\n", err);
            clReleaseContext(context);
            context = nullptr;
            return;
        }

        available = true;
        std::string platformName = gpuInfoString(platform, CL_PLATFORM_NAME);
        std::string deviceName = gpuInfoString(device, CL_DEVICE_NAME);
        std::string deviceVendor = gpuInfoString(device, CL_DEVICE_VENDOR);
        fprintf(stderr, "[GPU] OpenCL context initialised successfully "
                        "(platforms found: %u)\n", numPlatforms);
        fprintf(stderr, "[GPU] Selected platform: %s\n", platformName.c_str());
        fprintf(stderr, "[GPU] Selected device: %s (%s)\n", deviceName.c_str(), deviceVendor.c_str());
    }

    ~GpuContext() {
        if (queue)   { clFlush(queue); clFinish(queue); clReleaseCommandQueue(queue); }
        if (context) clReleaseContext(context);
    }
};

static GpuContext s_clCtx;

// ── Shared program cache (cl_program is thread-safe after clBuildProgram) ──
// Compile each source string only once; threads then create their own kernels.
// The program pointer is *also* memoised on the FunctionCommandRE instance
// (FunctionCommandRE::gpuProgramCache) so the hot path can skip this map
// entirely after the first dispatch.
static std::unordered_map<std::string, cl_program> s_programCache;
static std::mutex                                   s_programCacheMutex;

// Extract "kernelName" from "__kernel void kernelName("
static std::string gpuExtractKernelName(const std::string& src) {
    const std::string marker = "__kernel void ";
    auto pos = src.find(marker);
    if (pos == std::string::npos) return "kernel";
    pos += marker.size();
    auto end = src.find('(', pos);
    if (end == std::string::npos) return "kernel";
    std::string name = src.substr(pos, end - pos);
    // trim trailing whitespace
    while (!name.empty() && (name.back() == ' ' || name.back() == '\t')) name.pop_back();
    return name;
}

} // anonymous namespace

#endif // GPU_ENABLED

thread_local bool FunctionCommandRE::hasEncounteredReturn = false;

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
    firstUnit = nullptr;
    firstUnit = functionInfoRE->commmandRe;
    if (firstUnit == nullptr) {
        firstUnit = dynamic_cast<CommandRE *>(getFromMap(map, functionInfoRE->functionCall->firstCommandId));
        firstUnit = ((CommandRE*)firstUnit)->getUnit();
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
//            dataContainerNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
//                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            // Variable parameter found
            varCount++;
        }
    }

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
    totalDataContainerCount = totalVarCount + totalArrCount;
    //methodArgDataContainerAddr = new DataContainerValue*[totalDataContainerCount];

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
 * 
 * Returns: nextUnit after function completes (return statements are handled internally)
 */
RuleEngineInputUnits* FunctionCommandRE::process() {
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
     
     // Allocate pointer ranges from the memory pool
     // No memcpy needed - we get direct pointers into the pool!
     int totalSizeAllocated = totalDataContainerCount + argSize;
     
    memMaintainer->allocateDual(totalSizeAllocated);
    currentAsk = memMaintainer->currentAsk;

     // Get direct pointers to the allocated ranges
     DataContainerValueFunctionCommandRE** methodArgDataContainerCurrentValArray = currentAsk;
     DataContainerValueFunctionCommandRE** methodCalledDataContainerValueArray = currentAsk + totalDataContainerCount;
     
    for (int i = 0; i < argSize; i++) {
#ifdef DEBUG_BUILD
        // Record the argument value being passed for debugging
        debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
        // Save the current value of ALL function variables (for complete restoration)
        methodArgDataContainerAddr[i]->setValueInDataContainerValueFunctionCommandRE(methodArgDataContainerCurrentValArray[i]);
        // Save the current value of the function parameter and copy from calling argument in one operation
        methodCalledOriginalPlaceHolderAddrs[i]->saveValueAndCopyFrom(methodCalledDataContainerValueArray[i], methodCallingOriginalPlaceHolderAddrs[i]);
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
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        methodArgDataContainerAddr[i]->setValueInDataContainerValueFunctionCommandRE(methodArgDataContainerCurrentValArray[i]);
    }

    // ==================== PHASE 2: FUNCTION BODY EXECUTION ====================
    
    /**
     * UNIT CHAIN EXECUTION:
     * Execute the function body by traversing the linked unit chain.
     * Each unit's process() method executes the unit and returns the next unit to execute.
     * Execution continues until there are no more units (nullptr is returned).
     * 
     * This forms the core execution loop that processes all statements in the function body.
     */
    unit = firstUnit;
    while(unit != nullptr) {
        unit = unit->process();  // Execute current unit and get next unit
    }

    // ==================== PHASE 3: CONTEXT RESTORATION AND CLEANUP ====================

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
     *
     * LOCAL VARIABLE RESTORATION:
     * Restore all local variables (non-parameters) to their pre-function-call state.
     * This ensures that each function call has isolated local variable scope.
     */
    for(int i = argSize; i < totalDataContainerCount; i++) {
        methodArgDataContainerAddr[i]->copyDataContainerValueFunctionCommandRE(methodArgDataContainerCurrentValArray[i]);
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
         * UNIFIED CALL-BY-REFERENCE RESTORATION AND PROPAGATION:
         * This single method call performs three critical operations atomically:
         * 1. Saves the final computed value from the function parameter
         * 2. Restores the function parameter to its pre-call state
         * 3. Propagates the final value to the calling context variable
         *
         * This eliminates the need for temporary storage (methodArgContainerFinalValue/Ptr)
         * and reduces overhead by combining two virtual function calls into one.
         *
         * RECURSIVE FUNCTION SAFETY:
         * The atomic nature of this operation ensures correct behavior even when
         * methodCallingOriginalPlaceHolderAddrs[i] and methodCalledOriginalPlaceHolderAddrs[i]
         * point to the same memory location (recursive calls).
         */
        methodCalledOriginalPlaceHolderAddrs[i]->saveRestoreAndPropagate(methodCalledDataContainerValueArray[i], methodCallingOriginalPlaceHolderAddrs[i]);
    }

    // Deallocate the memory pool ranges
    memMaintainer->deallocateDual(totalSizeAllocated);
    
    // Function completed - return control to the next unit after the function call
    return nextUnit;
}

#ifdef GPU_ENABLED
void GPUFunctionCommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    FunctionCommandRE::setFields(map);

    s_clCtx.init();
    if (!s_clCtx.available) {
        fprintf(stderr, "[GPU] OpenCL unavailable – GPU function will be skipped at runtime\n");
        return;
    }

    const std::string& kernelSrc = functionInfoRE->openClCode;

    {
        std::lock_guard<std::mutex> cacheLock(s_programCacheMutex);
        auto srcIt = s_programCache.find(kernelSrc);
        if (srcIt != s_programCache.end()) {
            gpuProgram = srcIt->second;
        } else {
            cl_int err;
            const char* csrc = kernelSrc.c_str();
            size_t srcLen    = kernelSrc.size();
            gpuProgram = clCreateProgramWithSource(s_clCtx.context, 1, &csrc, &srcLen, &err);
            err = clBuildProgram(gpuProgram, 1, &s_clCtx.device, "-cl-std=CL1.2", nullptr, nullptr);
            if (err != CL_SUCCESS) {
                size_t logLen = 0;
                clGetProgramBuildInfo(gpuProgram, s_clCtx.device, CL_PROGRAM_BUILD_LOG, 0, nullptr, &logLen);
                std::string log(logLen, '\0');
                clGetProgramBuildInfo(gpuProgram, s_clCtx.device, CL_PROGRAM_BUILD_LOG, logLen, &log[0], nullptr);
                fprintf(stderr, "[GPU] Kernel build error:\n%s\n", log.c_str());
                clReleaseProgram(gpuProgram);
                gpuProgram = nullptr;
                return;
            }
            s_programCache[kernelSrc] = gpuProgram;
        }
    }

    std::string kname = gpuExtractKernelName(kernelSrc);
    cl_int err;
    gpuKernel = clCreateKernel(gpuProgram, kname.c_str(), &err);
    if (err != CL_SUCCESS) {
        fprintf(stderr, "[GPU] clCreateKernel '%s' failed: %d\n", kname.c_str(), err);
        fprintf(stderr, "[GPU] FATAL: GPU kernel unavailable in setFields()\n");
        exit(1);
    }

    gpuParallelismIdxs = functionInfoRE->gpuParallelismArgIndices;
    gpuWorkDim = (cl_uint)gpuParallelismIdxs.size();

    std::set<int> parallelismSet(gpuParallelismIdxs.begin(), gpuParallelismIdxs.end());
    gpuDataArgIndices.reserve(argSize);
    for (int i = 0; i < argSize; i++) {
        if (parallelismSet.count(i) == 0)
            gpuDataArgIndices.push_back(i);
    }
    gpuDataArgCount = (int)gpuDataArgIndices.size();
    for (int di = 0; di < gpuDataArgCount; di++) {
        gpuAvCache[di] = static_cast<ArrayDataContainerValue*>(
            methodCallingOriginalPlaceHolderAddrs[gpuDataArgIndices[di]])->arrayValue;
    }
}

RuleEngineInputUnits* GPUFunctionCommandRE::process() {
    // -- global_work_size from calling-context scalar values at parallelism arg positions --
    for (int pi = 0; pi < gpuWorkDim; pi++) {
        gpuGlobalWorkSize[pi] = (size_t)(static_cast<DoublePtr*>(
            methodCallingOriginalPlaceHolderAddrs[gpuParallelismIdxs[pi]])->value);
    }

    // -- Upload float* arrays directly to GPU (no staging, no conversion) --
    gpuErr         = CL_SUCCESS;
    gpuBufferError = false;

    for (int di = 0; di < gpuDataArgCount; di++) {
        gpuNeeded = (size_t)gpuAvCache[di]->totalSize * sizeof(float);

        gpuBufferReallocated = false;
        if (!gpuBuffers[di] || gpuBufferSizes[di] != gpuNeeded) {
            if (gpuBuffers[di]) { clReleaseMemObject(gpuBuffers[di]); gpuBuffers[di] = nullptr; }
            gpuBuffers[di] = clCreateBuffer(
                s_clCtx.context, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                gpuNeeded, gpuAvCache[di]->val, &gpuErr);
            if (gpuErr == CL_SUCCESS) {
                gpuBufferSizes[di]   = gpuNeeded;
                gpuBufferReallocated = true;
            }
        } else {
            gpuErr = clEnqueueWriteBuffer(s_clCtx.queue, gpuBuffers[di], CL_FALSE, 0, gpuNeeded, gpuAvCache[di]->val, 0, nullptr, nullptr);
        }

        if (gpuErr != CL_SUCCESS) {
            fprintf(stderr, "[GPU] buffer setup failed (arg %d): error %d\n", di, gpuErr);
            gpuBufferError = true;
            break;
        }
        if (gpuBufferReallocated) {
            gpuSetErr = clSetKernelArg(gpuKernel, (cl_uint)di, sizeof(cl_mem), &gpuBuffers[di]);
            if (gpuSetErr != CL_SUCCESS) {
                fprintf(stderr, "[GPU-DBG] clSetKernelArg arg=%d failed err=%d\n", di, gpuSetErr);
            }
        }
    }

    // Skip dispatch when any globalWorkSize dimension is 0
    gpuZeroWorkSize = false;
    for (cl_uint wi = 0; wi < gpuWorkDim; wi++) {
        if (gpuGlobalWorkSize[wi] == 0) { gpuZeroWorkSize = true; break; }
    }
    if (gpuZeroWorkSize) fprintf(stderr, "[GPU-DBG] SKIPPING dispatch: zeroWorkSize\n");
    if (gpuBufferError)  fprintf(stderr, "[GPU-DBG] SKIPPING dispatch: bufferError\n");

    if (!gpuBufferError && !gpuZeroWorkSize) {
        gpuErr = clEnqueueNDRangeKernel(
            s_clCtx.queue, gpuKernel, gpuWorkDim,
            nullptr, gpuGlobalWorkSize, nullptr,
            0, nullptr, nullptr);

        if (gpuErr == CL_SUCCESS) {
            // -- Queue all reads as non-blocking, then sync once --
            for (int di = 0; di < gpuDataArgCount; di++) {
                clEnqueueReadBuffer(
                    s_clCtx.queue, gpuBuffers[di], CL_FALSE, 0,
                    gpuBufferSizes[di], gpuAvCache[di]->val,
                    0, nullptr, nullptr);
            }
            clFinish(s_clCtx.queue);
        } else {
            fprintf(stderr, "[GPU] clEnqueueNDRangeKernel failed: %d\n", gpuErr);
        }
    }

    return nextUnit;
}
#endif // GPU_ENABLED

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
RuleEngineInputUnits* NINF::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            doublePtr->value = -std::numeric_limits<double>::infinity();
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayDataContainerValue* arrayDataContainerValue = dynamic_cast<ArrayDataContainerValue*>(dataContainerValue)) {
            auto arrayValue = arrayDataContainerValue->arrayValue;
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = -std::numeric_limits<float>::infinity();
            }
        }
    }
    return nextUnit;
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
RuleEngineInputUnits* PINF::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            doublePtr->value = std::numeric_limits<double>::infinity();
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayDataContainerValue* arrayDataContainerValue = dynamic_cast<ArrayDataContainerValue*>(dataContainerValue)) {
            auto arrayValue = arrayDataContainerValue->arrayValue;
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = std::numeric_limits<float>::infinity();
            }
        }
    }
    return nextUnit;
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
RuleEngineInputUnits* RAND::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DataContainerValue* dataContainerValue = methodArgDataContainerAddr[0];
        
        // Check if this is a variable (DoublePtr)
        if(DoublePtr* doublePtr = dynamic_cast<DoublePtr*>(dataContainerValue)) {
            doublePtr->value = dis(gen);
        }
        // Check if this is an array (ArrayValue)
        else if(ArrayDataContainerValue* pArrayDataContainerValueValue = dynamic_cast<ArrayDataContainerValue*>(dataContainerValue)) {
            ArrayValue* arrayValue = pArrayDataContainerValueValue->arrayValue;
            for(int i = 0; i < arrayValue->totalSize; i++) {
                arrayValue->val[i] = dis(gen);
            }
        }
    }
    return nextUnit;
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
RuleEngineInputUnits* ABS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::abs(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* SIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::sin(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* COS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::cos(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* TAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::tan(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* ASIN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::asin(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* ACOS::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::acos(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* ATAN::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::atan(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* FLOOR::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::floor(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* CEIL::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::ceil(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* EXP::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::exp(doublePtr->value);
    }
    return nextUnit;
}

/**
 * LOG (Natural Logarithm) Built-in Function Implementation.
 *
 * Computes the natural logarithm (base e) of the input value using the
 * C standard library std::log().
 *
 * Mathematical Properties:
 * - Domain: (0, ∞)  (strictly positive real numbers)
 * - Range: (-∞, ∞)
 * - log(1) = 0
 * - log(e) = 1
 * - Inverse function of exponential (EXP)
 * - Non-positive inputs return NaN (IEEE 754 behaviour)
 *
 * Usage: LOG(variable) → variable = ln(variable)
 * Example: LOG(2.71828) → variable becomes ≈ 1.0
 */
RuleEngineInputUnits* LOG::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::log(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* SQRT::process() {
    if(functionCommandInfo->argumentsSize >= 1) {
        DoublePtr* doublePtr = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        doublePtr->value = std::sqrt(doublePtr->value);
    }
    return nextUnit;
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
RuleEngineInputUnits* POW::process() {
    if(functionCommandInfo->argumentsSize >= 2) {
        DoublePtr* doublePtr1 = static_cast<DoublePtr*>(methodArgDataContainerAddr[0]);
        DoublePtr* doublePtr2 = static_cast<DoublePtr*>(methodArgDataContainerAddr[1]);
        doublePtr1->value = std::pow(doublePtr1->value, doublePtr2->value);
    }
    return nextUnit;
}