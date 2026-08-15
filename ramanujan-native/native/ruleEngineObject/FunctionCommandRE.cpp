//
// Created by pranav on 28/3/24.
//

#include "FunctionCommandRE.h"
#include "DataContainerValueFunctionCommandREMemMaintainer.h"
#include "DebugPoint.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/DataContainerValueFunctionCommandRE.h"
#include "dataContainer/VariableRE.h"
#include "dataContainer/array/ArrayValue.h"
#include <vector>

#include <limits>
#include <random>

#ifdef GPU_ENABLED
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <set>

// ==================== OpenCL ====================
// On Android we must dlopen libOpenCL.so at runtime; the Khronos ICD loader
// cannot discover vendor libraries via /etc/OpenCL/vendors/*.icd on Android.
// opencl_loader.h does the dlopen and re-#defines all cl* to pfn_* pointers.
// On all other platforms it is a no-op and we link against the system OpenCL.
#ifdef __APPLE__
#include <OpenCL/cl.h>
// opencl_loader.h is included by FunctionCommandRE.h on non-Apple/Android
// builds.
#endif

// ---------------------------------------------------------------------------
// GPU diagnostics logging.
// On Android, fprintf(stderr, ...) is NOT connected to logcat, so every GPU
// init/build/dispatch error printed to stderr was silently discarded on-device
// — leaving the real failure mode (kernel build log, dispatch error code,
// "GPU unavailable") invisible.  Route all diagnostics to logcat instead:
//   adb logcat -s RamanujanGPU
// On every other platform this is a plain fprintf(stderr, ...).
// ---------------------------------------------------------------------------
#ifdef __ANDROID__
#include <android/log.h>
#define RJ_GPU_LOG(...) __android_log_print(ANDROID_LOG_ERROR, "RamanujanGPU", __VA_ARGS__)
#else
#define RJ_GPU_LOG(...) fprintf(stderr, __VA_ARGS__)
#endif

// ---------------------------------------------------------------------------
// Singleton OpenCL context  (created once, reused for all GPU dispatches)
// cl_context and cl_device_id are thread-safe to share across threads.
// cl_command_queue is shared (s_clCtx.queue), created once alongside the
// context. The shared context is initialised once under GpuContext::initMutex.
// ---------------------------------------------------------------------------
namespace {

static std::string gpuInfoString(cl_platform_id platformId,
                                 cl_platform_info infoKey) {
  size_t valueSize = 0;
  cl_int err = clGetPlatformInfo(platformId, infoKey, 0, nullptr, &valueSize);
  if (err != CL_SUCCESS || valueSize == 0)
    return "unknown";
  std::string value(valueSize, '\0');
  err = clGetPlatformInfo(platformId, infoKey, valueSize, &value[0], nullptr);
  if (err != CL_SUCCESS)
    return "unknown";
  while (!value.empty() &&
         (value.back() == '\0' || value.back() == '\n' || value.back() == '\r'))
    value.pop_back();
  return value;
}

static std::string gpuInfoString(cl_device_id deviceId,
                                 cl_device_info infoKey) {
  size_t valueSize = 0;
  cl_int err = clGetDeviceInfo(deviceId, infoKey, 0, nullptr, &valueSize);
  if (err != CL_SUCCESS || valueSize == 0)
    return "unknown";
  std::string value(valueSize, '\0');
  err = clGetDeviceInfo(deviceId, infoKey, valueSize, &value[0], nullptr);
  if (err != CL_SUCCESS)
    return "unknown";
  while (!value.empty() &&
         (value.back() == '\0' || value.back() == '\n' || value.back() == '\r'))
    value.pop_back();
  return value;
}

[[maybe_unused]] static void
rjGpuLogDeviceInfo(cl_platform_id platform, cl_device_id device,
                   cl_uint numPlatforms) {
  std::string platformName = gpuInfoString(platform, CL_PLATFORM_NAME);
  std::string deviceName = gpuInfoString(device, CL_DEVICE_NAME);
  std::string deviceVendor = gpuInfoString(device, CL_DEVICE_VENDOR);
  RJ_GPU_LOG("[GPU] OpenCL context initialised successfully "
             "(platforms found: %u)\n",
             numPlatforms);
  RJ_GPU_LOG("[GPU] Selected platform: %s\n", platformName.c_str());
  RJ_GPU_LOG("[GPU] Selected device: %s (%s)\n", deviceName.c_str(),
             deviceVendor.c_str());
}

[[maybe_unused]] static void rjGpuLogDispatchState(bool zeroWorkSize,
                                                   bool bufferError) {
  if (zeroWorkSize)
    RJ_GPU_LOG("[GPU-DBG] SKIPPING dispatch: zeroWorkSize\n");
  if (bufferError)
    RJ_GPU_LOG("[GPU-DBG] SKIPPING dispatch: bufferError\n");
}

[[maybe_unused]] static void
rjGpuLogLoadDiagnostics(ArrayValue *arrayValue, bool attempted, cl_int err) {
  if (attempted) {
    RJ_GPU_LOG("[GPU_LOAD] uploaded %zu bytes, val[0]=%.1f, err=%d\n",
               arrayValue->gpuBufferBytes, arrayValue->val[0], err);
  } else {
    RJ_GPU_LOG("[GPU_LOAD] no gpuBuffer yet (val[0]=%.1f), skipped\n",
               arrayValue ? arrayValue->val[0] : -999.0f);
  }
}

// ---------------------------------------------------------------------------
// Logs the device memory/work-group limits and probes the single largest buffer
// the Phi-3 kernel needs: logits_partial = 32064*3072 floats = 394 MB in ONE
// allocation.  Mobile Adreno/Mali often cap CL_DEVICE_MAX_MEM_ALLOC_SIZE well
// below that, so this single clCreateBuffer can fail on-device while succeeding
// on desktop — leaving the logits buffer unallocated/garbage → fixed argmax.
// ---------------------------------------------------------------------------
[[maybe_unused]] static void rjGpuLogDeviceLimits(cl_context ctx,
                                                  cl_device_id dev) {
  cl_ulong maxAlloc = 0, globalMem = 0;
  size_t maxWg = 0;
  clGetDeviceInfo(dev, CL_DEVICE_MAX_MEM_ALLOC_SIZE, sizeof(maxAlloc), &maxAlloc,
                  nullptr);
  clGetDeviceInfo(dev, CL_DEVICE_GLOBAL_MEM_SIZE, sizeof(globalMem), &globalMem,
                  nullptr);
  clGetDeviceInfo(dev, CL_DEVICE_MAX_WORK_GROUP_SIZE, sizeof(maxWg), &maxWg,
                  nullptr);
  RJ_GPU_LOG("[GPU-LIMITS] maxMemAlloc=%llu MB, globalMem=%llu MB, "
             "maxWorkGroup=%zu\n",
             (unsigned long long)(maxAlloc >> 20),
             (unsigned long long)(globalMem >> 20), maxWg);
  size_t bigBytes = (size_t)32064 * 3072 * sizeof(float);
  cl_int err;
  cl_mem big = clCreateBuffer(ctx, CL_MEM_READ_WRITE, bigBytes, nullptr, &err);
  RJ_GPU_LOG("[GPU-LIMITS] logits_partial alloc test (%zu MB): err=%d (0=OK, "
             "-61=INVALID_BUFFER_SIZE, -4=ALLOCATION_FAILURE)\n",
             (size_t)(bigBytes >> 20), err);
  if (err == CL_SUCCESS)
    clReleaseMemObject(big);
}

// ---------------------------------------------------------------------------
// One-time on-device numeric self-test for the 4-bit dequant.
// The Phi-3 kernels recover each 4-bit weight with float division-by-powers-of-
// two + floor(), which needs a bit-exact single-precision divide.  Apple's GPU
// rounds it exactly; Adreno/Mali divide is only <=2.5 ULP, so floor(packed/2^k)
// can drop to the wrong integer and corrupt every nibble.  This extracts one
// known packed value two ways (division+floor vs. exact integer bitmask) and
// logs both to logcat so the divide path can be compared against the exact one.
// Remove once the dequant divergence is resolved.
// ---------------------------------------------------------------------------
[[maybe_unused]] static void rjGpuDequantSelfTest(cl_context ctx,
                                                  cl_device_id dev,
                                                  cl_command_queue q) {
  // Stresses the exact dequant recurrence used by the 4-bit matmul kernels,
  // against adversarial packed values that mirror the real down-projection
  // weights: large magnitudes (near 2^24) and exact nibble-boundary multiples
  // where floor(packed/D) is most likely to underflow to the wrong integer on
  // hardware with non-correctly-rounded division (e.g. Adreno).
  //
  // For each test value the kernel decodes 6 nibbles two ways:
  //   (a) div+floor  – the recurrence emitted by GpuFunctionBodyConverter
  //   (b) bitmask    – exact integer decode (reference truth)
  // and writes: [expected_packed, |diff| summed over nibbles].
  static const int NTEST = 8;
  static const char *src =
      "inline float decode_diff(float packed) {\n"
      "  float p = packed;\n"
      "  float a5 = floor(p/1048576.0f); p = p - a5*1048576.0f;\n"
      "  float a4 = floor(p/65536.0f);   p = p - a4*65536.0f;\n"
      "  float a3 = floor(p/4096.0f);    p = p - a3*4096.0f;\n"
      "  float a2 = floor(p/256.0f);     p = p - a2*256.0f;\n"
      "  float a1 = floor(p/16.0f);      p = p - a1*16.0f;\n"
      "  float a0 = p;\n"
      "  uint u = (uint)packed;\n"
      "  float b5=(float)((u>>20)&15u), b4=(float)((u>>16)&15u);\n"
      "  float b3=(float)((u>>12)&15u), b2=(float)((u>>8)&15u);\n"
      "  float b1=(float)((u>>4)&15u),  b0=(float)(u&15u);\n"
      "  return fabs(a5-b5)+fabs(a4-b4)+fabs(a3-b3)+\n"
      "         fabs(a2-b2)+fabs(a1-b1)+fabs(a0-b0);\n"
      "}\n"
      "__kernel void rj_dequant_selftest(__global const float* in,\n"
      "                                  __global float* out) {\n"
      "  int i = get_global_id(0);\n"
      "  float packed = in[i];\n"
      "  out[i*2+0] = packed;\n"
      "  out[i*2+1] = decode_diff(packed);\n"
      "}\n";
  auto nib = [](int n5, int n4, int n3, int n2, int n1, int n0) -> float {
    return (float)(((unsigned)n5 << 20) | ((unsigned)n4 << 16) |
                   ((unsigned)n3 << 12) | ((unsigned)n2 << 8) |
                   ((unsigned)n1 << 4) | (unsigned)n0);
  };
  float in[NTEST] = {
      nib(1, 2, 3, 4, 5, 6),        // 1193046  (original sanity case)
      nib(15, 0, 0, 0, 0, 0),       // 15728640 exact boundary, top nibble
      nib(15, 15, 15, 15, 15, 15),  // 16777215 = 2^24-1, all nibbles max
      nib(8, 0, 0, 0, 0, 0),        //  8388608 = 2^23 exact
      nib(1, 0, 0, 0, 0, 0),        //  1048576 exact multiple
      nib(15, 8, 4, 0, 0, 0),       // large, some zero low nibbles
      nib(7, 15, 0, 0, 15, 0),      // mixed boundary
      nib(15, 14, 13, 12, 11, 10),  // large, no boundary
  };
  cl_int err;
  cl_program prog = clCreateProgramWithSource(ctx, 1, &src, nullptr, &err);
  if (err != CL_SUCCESS) {
    RJ_GPU_LOG("[GPU-SELFTEST] create failed: %d\n", err);
    return;
  }
  err = clBuildProgram(prog, 1, &dev, "-cl-std=CL1.2", nullptr, nullptr);
  if (err != CL_SUCCESS) {
    size_t ln = 0;
    clGetProgramBuildInfo(prog, dev, CL_PROGRAM_BUILD_LOG, 0, nullptr, &ln);
    std::string log(ln, '\0');
    clGetProgramBuildInfo(prog, dev, CL_PROGRAM_BUILD_LOG, ln, &log[0], nullptr);
    RJ_GPU_LOG("[GPU-SELFTEST] build failed: %d\n%s\n", err, log.c_str());
    clReleaseProgram(prog);
    return;
  }
  cl_kernel k = clCreateKernel(prog, "rj_dequant_selftest", &err);
  cl_mem inbuf = clCreateBuffer(ctx, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                                NTEST * sizeof(float), in, &err);
  cl_mem out = clCreateBuffer(ctx, CL_MEM_WRITE_ONLY,
                              2 * NTEST * sizeof(float), nullptr, &err);
  clSetKernelArg(k, 0, sizeof(cl_mem), &inbuf);
  clSetKernelArg(k, 1, sizeof(cl_mem), &out);
  size_t gws = NTEST;
  err = clEnqueueNDRangeKernel(q, k, 1, nullptr, &gws, nullptr, 0, nullptr,
                               nullptr);
  if (err != CL_SUCCESS)
    RJ_GPU_LOG("[GPU-SELFTEST] dispatch failed: %d\n", err);
  float h[2 * NTEST] = {0};
  clEnqueueReadBuffer(q, out, CL_TRUE, 0, sizeof(h), h, 0, nullptr, nullptr);
  int broken = 0;
  for (int i = 0; i < NTEST; ++i) {
    RJ_GPU_LOG("[GPU-SELFTEST] packed=%.1f  nibble_diff=%.1f %s\n", h[i * 2 + 0],
               h[i * 2 + 1],
               h[i * 2 + 1] != 0.0f ? "<<< DIV+FLOOR WRONG" : "ok");
    if (h[i * 2 + 1] != 0.0f)
      ++broken;
  }
  RJ_GPU_LOG("[GPU-SELFTEST] summary: %d / %d packed values decode WRONG via "
             "div+floor (0 = dequant is fine)\n",
             broken, NTEST);
  clReleaseMemObject(inbuf);
  clReleaseMemObject(out);
  clReleaseKernel(k);
  clReleaseProgram(prog);
}

struct GpuContext {
  cl_platform_id platform = nullptr;
  cl_device_id device = nullptr;
  cl_context context = nullptr;
  cl_command_queue queue = nullptr;
  bool initialized = false;
  bool available = false;
  std::mutex initMutex; // guards one-time initialisation only

  void init() {
    std::lock_guard<std::mutex> lk(initMutex);
    if (initialized)
      return;
    initialized = true;

#ifdef __ANDROID__
    // On Android the Khronos ICD loader cannot find /etc/OpenCL/vendors/*.icd
    // files (they don't exist). Load libOpenCL.so via dlopen first so that
    // all subsequent cl* calls resolve to the vendor implementation.
    if (!openclLoad()) {
      RJ_GPU_LOG("[GPU] Android: could not dlopen libOpenCL.so – "
                      "GPU execution unavailable.\n"
                      "  Ensure AndroidManifest.xml contains:\n"
                      "  <uses-native-library android:name=\"libOpenCL.so\" "
                      "android:required=\"false\"/>\n");
      return;
    }
#endif

    cl_int err;

    // Step 1: count available platforms before fetching them (safer two-step
    // query)
    cl_uint numPlatforms = 0;
    err = clGetPlatformIDs(0, nullptr, &numPlatforms);
    if (err != CL_SUCCESS || numPlatforms == 0) {
      RJ_GPU_LOG(
              "[GPU] clGetPlatformIDs count query failed "
              "(err=%d, numPlatforms=%u).\n"
              "  macOS: ensure the binary links -framework OpenCL, not the "
              "Khronos ICD loader.\n"
              "  Linux: sudo apt install ocl-icd-opencl-dev && install GPU "
              "vendor drivers.\n",
              err, numPlatforms);
      return;
    }

    // Step 2: retrieve the first platform
    err = clGetPlatformIDs(1, &platform, nullptr);
    if (err != CL_SUCCESS) {
      RJ_GPU_LOG("[GPU] clGetPlatformIDs retrieve failed: err=%d\n", err);
      return;
    }

    // Prefer a GPU device; fall back to any device (e.g. CPU OpenCL on Linux)
    err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, &device, nullptr);
    if (err != CL_SUCCESS) {
      RJ_GPU_LOG(
              "[GPU] No GPU device found (err=%d), falling back to "
              "CL_DEVICE_TYPE_ALL\n",
              err);
      err = clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 1, &device, nullptr);
    }
    if (err != CL_SUCCESS) {
      RJ_GPU_LOG("[GPU] clGetDeviceIDs failed: err=%d\n", err);
      return;
    }

    context = clCreateContext(nullptr, 1, &device, nullptr, nullptr, &err);
    if (err != CL_SUCCESS) {
      RJ_GPU_LOG("[GPU] clCreateContext failed: err=%d\n", err);
      return;
    }

    // Create a single shared command queue (created once, reused for all kernel
    // dispatches)
#if defined(CL_VERSION_2_0)
    const cl_queue_properties qprops[] = {0};
    queue = clCreateCommandQueueWithProperties(context, device, qprops, &err);
#else
#ifdef __APPLE__
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
#endif
    queue = clCreateCommandQueue(context, device, 0, &err);
#ifdef __APPLE__
#pragma clang diagnostic pop
#endif
#endif
    if (err != CL_SUCCESS) {
      RJ_GPU_LOG("[GPU] clCreateCommandQueue failed: %d\n", err);
      clReleaseContext(context);
      context = nullptr;
      return;
    }

    available = true;
    // Optional startup diagnostics; enable when investigating GPU devices.
    // rjGpuLogDeviceInfo(platform, device, numPlatforms);
    // rjGpuLogDeviceLimits(context, device);
    // rjGpuDequantSelfTest(context, device, queue);
  }

  ~GpuContext() {
    if (queue) {
      clFlush(queue);
      clFinish(queue);
      clReleaseCommandQueue(queue);
    }
    if (context)
      clReleaseContext(context);
  }
};

static GpuContext s_clCtx;

// ── Shared program cache (cl_program is thread-safe after clBuildProgram) ──
// Compile each source string only once; threads then create their own kernels.
// The program pointer is *also* memoised on the FunctionCommandRE instance
// (FunctionCommandRE::gpuProgramCache) so the hot path can skip this map
// entirely after the first dispatch.
static std::unordered_map<std::string, cl_program> s_programCache;
static std::mutex s_programCacheMutex;

// Extract "kernelName" from "__kernel void kernelName("
static std::string gpuExtractKernelName(const std::string &src) {
  const std::string marker = "__kernel void ";
  auto pos = src.find(marker);
  if (pos == std::string::npos)
    return "kernel";
  pos += marker.size();
  auto end = src.find('(', pos);
  if (end == std::string::npos)
    return "kernel";
  std::string name = src.substr(pos, end - pos);
  // trim trailing whitespace
  while (!name.empty() && (name.back() == ' ' || name.back() == '\t'))
    name.pop_back();
  return name;
}

} // anonymous namespace

#endif // GPU_ENABLED

thread_local bool FunctionCommandRE::hasEncounteredReturn = false;

/**
 * Constructor for FunctionCommandRE.
 * Initializes a function call execution context by setting up the relationship
 * between the function call information (caller side) and function definition
 * (callee side).
 *
 * @param functionCommand Information about the function call being made (caller
 * context)
 * @param functionInfo Rule engine representation of the function definition
 * (callee context)
 */
FunctionCommandRE::FunctionCommandRE(FunctionCall *functionCommand,
                                     FunctionCallRE *functionInfo) {
  this->functionCommandInfo = functionCommand;
  this->functionInfoRE = functionInfo;
}

/**
 * FUNCTION EXECUTION OVERVIEW:
 *
 * The function execution process follows these key steps:
 * 1. functionInfoRE contains the function definition with parameters and
 * execution commands
 * 2. functionCommandInfo contains the calling context with argument values
 * 3. During function start: Parameter variables receive argument values, local
 * variables are initialized
 * 4. During function completion: Final parameter values are propagated back to
 * calling context, and all function variables are restored to their pre-call
 * state
 *
 * CRITICAL CONSIDERATION FOR RECURSIVE FUNCTIONS:
 * The restoration process must carefully preserve variable states to handle
 * recursive calls correctly. Each recursive call creates its own variable scope
 * that must be properly isolated and restored.
 *
 * CALL-BY-REFERENCE SEMANTICS:
 * This system uses call-by-reference semantics where parameters are modified
 * in-place. There are no explicit return statements - instead, parameter
 * modifications are propagated back to the calling context as the mechanism for
 * returning computed results.
 */

/**
 * Sets up field mappings and initializes data structures for function
 * execution. This method performs comprehensive parameter mapping between
 * calling and called functions, including separation of variables and arrays,
 * address mapping setup, and local storage initialization.
 *
 * @param map Global map containing all rule engine objects indexed by their IDs
 */
void FunctionCommandRE::setFields(
    std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
  // Initialize the function definition's fields first
  functionInfoRE->setFields(map);

  // Set the total number of arguments for this function call
  argSize = functionCommandInfo->argumentsSize;

  // Determine the first command to execute in the function body
  firstUnit = nullptr;
  firstUnit = functionInfoRE->commmandRe;
  if (firstUnit == nullptr) {
    firstUnit = dynamic_cast<CommandRE *>(
        getFromMap(map, functionInfoRE->functionCall->firstCommandId));
    firstUnit = ((CommandRE *)firstUnit)->getUnit();
  }

  /**
   * PARAMETER MAPPING PHASE:
   * Categorize function parameters into variables and arrays, and establish
   * address mappings between calling arguments and function parameters.
   */

  std::list<DataContainerValue *> methodCalledOriginalPlaceHolderAddrsList;
  std::list<DataContainerValue *> methodCallingOriginalPlaceHolderAddrsList;

  /**
   * ARGUMENT CATEGORIZATION LOOP:
   * Iterate through all function parameters to separate variables from arrays
   * and establish bidirectional address mappings between caller and callee
   * contexts.
   */
  for (int i = 0; i < functionInfoRE->argSize; i++) {
    AbstractDataContainer *calledArg =
        dynamic_cast<AbstractDataContainer *>(functionInfoRE->arguments[i]);
    AbstractDataContainer *callingArg = dynamic_cast<AbstractDataContainer *>(
        map->at(functionCommandInfo->arguments[i]));

    methodCalledOriginalPlaceHolderAddrsList.push_back(calledArg->valPtr);
    methodCallingOriginalPlaceHolderAddrsList.push_back(callingArg->valPtr);

    // Build name mapping for debugging (works for both variables and arrays)
    if (dynamic_cast<ArrayRE *>(functionInfoRE->arguments[i]) != nullptr) {
      // Array parameter found
      arrCount++;
      // Build name mapping for debugging purposes
      //            dataContainerNameMethodMap.insert(std::make_pair(((ArrayRE
      //            *) map->at(functionCommandInfo->arguments[i]))->name,
      //                                                ((ArrayRE *)
      //                                                functionInfoRE->arguments[i])->name));
    } else {
      // Variable parameter found
      varCount++;
    }
  }

  /**
   * POPULATE VARIABLE PARAMETER MAPPINGS:
   * Transfer variable address mappings from lists to arrays for indexed access.
   */
  for (int i = 0; i < argSize; i++) {
    methodCalledOriginalPlaceHolderAddrs[i] =
        methodCalledOriginalPlaceHolderAddrsList.front();
    methodCalledOriginalPlaceHolderAddrsList.pop_front();

    methodCallingOriginalPlaceHolderAddrs[i] =
        methodCallingOriginalPlaceHolderAddrsList.front();
    methodCallingOriginalPlaceHolderAddrsList.pop_front();
  }

  /**
   * LOCAL VARIABLE AND ARRAY ANALYSIS:
   * Analyze all variables and arrays declared within the function scope
   * (including both parameters and local declarations) to set up complete
   * variable management.
   */
  std::list<DataContainerValue *> methodArgDataContainerAddrList;

  for (int i = 0; i < functionInfoRE->functionCall->allVariablesInMethodSize;
       i++) {
    AbstractDataContainer *dataContainer =
        dynamic_cast<AbstractDataContainer *>(
            functionInfoRE->allVariablesInMethod[i]);
    methodArgDataContainerAddrList.push_back(dataContainer->valPtr);

    if (dynamic_cast<ArrayRE *>(functionInfoRE->allVariablesInMethod[i]) !=
        nullptr) {
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
  // methodArgDataContainerAddr = new
  // DataContainerValue*[totalDataContainerCount];

  /**
   * POPULATE COMPLETE VARIABLE ADDRESS MAPPING:
   * Store addresses of all variables in the function (parameters + locals).
   */
  for (int i = 0; i < totalDataContainerCount; i++) {
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
 * call semantics, including recursive calls and comprehensive memory
 * management.
 *
 * Returns: nextUnit after function completes (return statements are handled
 * internally)
 */
RuleEngineInputUnits *FunctionCommandRE::process() {
  // ==================== DEBUG SETUP ====================
#ifdef DEBUG_BUILD
  // Get debug point for tracking function call execution
  std::shared_ptr<DebugPoint> debugPoint =
      debugger->getDebugPointToBeCommitted();
#endif

  // ==================== PHASE 1: PARAMETER SETUP AND DATA CONTAINER CONTEXT
  // SAVING ====================
  /**
   * VARIABLE PARAMETER SETUP:
   * For each variable parameter passed to the function:
   * 1. Save the current value of the function parameter variable (for
   * restoration)
   * 2. Save the current value of the calling argument variable (for
   * restoration)
   * 3. Copy the calling function's argument value to the function parameter
   *
   * This establishes the parameter passing mechanism while preserving state for
   * restoration.
   */

  // Allocate pointer ranges from the memory pool
  // No memcpy needed - we get direct pointers into the pool!
  int totalSizeAllocated = totalDataContainerCount + argSize;

  memMaintainer->allocateDual(totalSizeAllocated);
  currentAsk = memMaintainer->currentAsk;

  // Get direct pointers to the allocated ranges
  DataContainerValueFunctionCommandRE **methodArgDataContainerCurrentValArray =
      currentAsk;
  DataContainerValueFunctionCommandRE **methodCalledDataContainerValueArray =
      currentAsk + totalDataContainerCount;

  for (int i = 0; i < argSize; i++) {
#ifdef DEBUG_BUILD
    // Record the argument value being passed for debugging
    debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
    // Save the current value of ALL function variables (for complete
    // restoration)
    methodArgDataContainerAddr[i]
        ->setValueInDataContainerValueFunctionCommandRE(
            methodArgDataContainerCurrentValArray[i]);
    // Save the current value of the function parameter and copy from calling
    // argument in one operation
    methodCalledOriginalPlaceHolderAddrs[i]->saveValueAndCopyFrom(
        methodCalledDataContainerValueArray[i],
        methodCallingOriginalPlaceHolderAddrs[i]);
  }

#ifdef DEBUG_BUILD
  // Record data container name mappings for debugging purposes
  // This helps track which calling data containers correspond to which function
  // parameters
  for (auto it = dataContainerNameMethodMap.begin();
       it != dataContainerNameMethodMap.end(); it++) {
    debugPoint->addArrayInFuncCall(it->first, it->second);
  }
  debugger->commitDebugPoint();
#endif

  /**
   * LOCAL VARIABLE STATE PRESERVATION:
   * Save current values of all local variables (non-parameters) so they can be
   * restored after function execution. Local variables are indexed from argSize
   * onwards.
   */
  for (int i = argSize; i < totalDataContainerCount; i++) {
    methodArgDataContainerAddr[i]
        ->setValueInDataContainerValueFunctionCommandRE(
            methodArgDataContainerCurrentValArray[i]);
  }

  // ==================== PHASE 2: FUNCTION BODY EXECUTION ====================

  /**
   * UNIT CHAIN EXECUTION:
   * Execute the function body by traversing the linked unit chain.
   * Each unit's process() method executes the unit and returns the next unit to
   * execute. Execution continues until there are no more units (nullptr is
   * returned).
   *
   * This forms the core execution loop that processes all statements in the
   * function body.
   */
  unit = firstUnit;
  while (unit != nullptr) {
    unit = unit->process(); // Execute current unit and get next unit
  }

  // ==================== PHASE 3: CONTEXT RESTORATION AND CLEANUP
  // ====================

  /**
   * CRITICAL RESTORATION PHASE:
   * We must restore the calling context regardless of how function execution
   * completed. This is essential for recursive functions and proper stack
   * management.
   *
   * RECURSIVE FUNCTION EXAMPLE:
   * ```
   * func fibonacci(n) {
   *     if (n <= 1) return n;
   *     temp1 = n - 1;
   *     temp2 = n - 2;
   *     return fibonacci(temp1) + fibonacci(temp2);  // Multiple recursive
   * calls
   * }
   * ```
   *
   * Without proper restoration, variables like 'temp1' and 'temp2' would retain
   * values from inner recursive calls, corrupting the outer call's execution.
   *
   * LOCAL VARIABLE RESTORATION:
   * Restore all local variables (non-parameters) to their pre-function-call
   * state. This ensures that each function call has isolated local variable
   * scope.
   */
  for (int i = argSize; i < totalDataContainerCount; i++) {
    methodArgDataContainerAddr[i]->copyDataContainerValueFunctionCommandRE(
        methodArgDataContainerCurrentValArray[i]);
  }

  /**
   * VARIABLE PARAMETER RESTORATION AND CALL-BY-REFERENCE HANDLING:
   *
   * This critical phase handles both parameter restoration and
   * call-by-reference semantics. The order of operations is carefully designed
   * to handle recursive function calls correctly.
   *
   * IMPORTANT: This system uses CALL-BY-REFERENCE semantics, NOT return values.
   * All function parameters are passed by reference and their final values are
   * propagated back to the calling context. There is no explicit "return"
   * statement - instead, parameter modifications are the mechanism for passing
   * results back.
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

  for (int i = 0; i < argSize; i++) {
    /**
     * UNIFIED CALL-BY-REFERENCE RESTORATION AND PROPAGATION:
     * This single method call performs three critical operations atomically:
     * 1. Saves the final computed value from the function parameter
     * 2. Restores the function parameter to its pre-call state
     * 3. Propagates the final value to the calling context variable
     *
     * This eliminates the need for temporary storage
     * (methodArgContainerFinalValue/Ptr) and reduces overhead by combining two
     * virtual function calls into one.
     *
     * RECURSIVE FUNCTION SAFETY:
     * The atomic nature of this operation ensures correct behavior even when
     * methodCallingOriginalPlaceHolderAddrs[i] and
     * methodCalledOriginalPlaceHolderAddrs[i] point to the same memory location
     * (recursive calls).
     */
    methodCalledOriginalPlaceHolderAddrs[i]->saveRestoreAndPropagate(
        methodCalledDataContainerValueArray[i],
        methodCallingOriginalPlaceHolderAddrs[i]);
  }

  // Deallocate the memory pool ranges
  memMaintainer->deallocateDual(totalSizeAllocated);

  // Function completed - return control to the next unit after the function
  // call
  return nextUnit;
}

#ifdef GPU_ENABLED
void GPUFunctionCommandRE::setFields(
    std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
  FunctionCommandRE::setFields(map);

  s_clCtx.init();
  if (!s_clCtx.available) {
    fprintf(
        stderr,
        "[GPU] OpenCL unavailable – GPU function will be skipped at runtime\n");
    return;
  }

  const std::string &kernelSrc = functionInfoRE->openClCode;

  {
    std::lock_guard<std::mutex> cacheLock(s_programCacheMutex);
    auto srcIt = s_programCache.find(kernelSrc);
    if (srcIt != s_programCache.end()) {
      gpuProgram = srcIt->second;
    } else {
      cl_int err;
      const char *csrc = kernelSrc.c_str();
      size_t srcLen = kernelSrc.size();
      gpuProgram =
          clCreateProgramWithSource(s_clCtx.context, 1, &csrc, &srcLen, &err);
      // The 4-bit dequant recovers nibbles via floor(packed / 2^k).  OpenCL
      // fp32 divide is only <=2.5 ULP by default, so inside the hot matmul
      // accumulation loop the Adreno compiler may pick a fast reciprocal that
      // lands just under an integer boundary -> floor() drops a nibble ->
      // corrupt weights (seen only in the deepest-accumulation down matmul).
      // Force IEEE correctly-rounded divide only for legacy floor-based
      // kernels. Shift/mask dequantization has no such requirement.
      bool needsCorrectlyRoundedDivide =
          kernelSrc.find("floor(") != std::string::npos;
      const char *buildOptions = needsCorrectlyRoundedDivide
                                     ? "-cl-std=CL1.2 "
                                       "-cl-fp32-correctly-rounded-divide-sqrt"
                                     : "-cl-std=CL1.2";
      err = clBuildProgram(gpuProgram, 1, &s_clCtx.device,
                           buildOptions, nullptr, nullptr);
      if (err != CL_SUCCESS && needsCorrectlyRoundedDivide) {
        RJ_GPU_LOG("[GPU] correctly-rounded-divide build failed (err=%d), "
                   "retrying without it\n",
                   err);
        err = clBuildProgram(gpuProgram, 1, &s_clCtx.device, "-cl-std=CL1.2",
                             nullptr, nullptr);
      }
      if (err != CL_SUCCESS) {
        size_t logLen = 0;
        clGetProgramBuildInfo(gpuProgram, s_clCtx.device, CL_PROGRAM_BUILD_LOG,
                              0, nullptr, &logLen);
        std::string log(logLen, '\0');
        clGetProgramBuildInfo(gpuProgram, s_clCtx.device, CL_PROGRAM_BUILD_LOG,
                              logLen, &log[0], nullptr);
        RJ_GPU_LOG("[GPU] Kernel build error:\n%s\n", log.c_str());
        clReleaseProgram(gpuProgram);
        gpuProgram = nullptr;
        return;
      }
      s_programCache[kernelSrc] = gpuProgram;
    }
  }

  std::string kname = gpuExtractKernelName(kernelSrc);
  gpuKernelName = kname; // diagnostic: remember for NaN-scan logging
  cl_int err;
  gpuKernel = clCreateKernel(gpuProgram, kname.c_str(), &err);
  if (err != CL_SUCCESS) {
    RJ_GPU_LOG("[GPU] clCreateKernel '%s' failed: %d\n", kname.c_str(),
            err);
    RJ_GPU_LOG("[GPU] FATAL: GPU kernel unavailable in setFields()\n");
    exit(1);
  }

#ifdef __ANDROID__
  // Query the kernel's real work-group-size cap once so the Android wave-size
  // heuristic in process() never forces a local size the driver would reject.
  clGetKernelWorkGroupInfo(gpuKernel, s_clCtx.device, CL_KERNEL_WORK_GROUP_SIZE,
                           sizeof(gpuMaxWorkGroupSize), &gpuMaxWorkGroupSize,
                           nullptr);
#endif

  gpuParallelismIdxs = functionInfoRE->gpuParallelismArgIndices;
  gpuWorkDim = (cl_uint)gpuParallelismIdxs.size();
  for (cl_uint pi = 0; pi < gpuWorkDim; pi++) {
    gpuWorkSizeValuePtr[pi] =
        &static_cast<DoublePtr *>(
             methodCallingOriginalPlaceHolderAddrs[gpuParallelismIdxs[pi]])
             ->value;
  }

  std::set<int> parallelismSet(gpuParallelismIdxs.begin(),
                               gpuParallelismIdxs.end());
  gpuDataArgIndices.reserve(argSize);
  for (int i = 0; i < argSize; i++) {
    if (parallelismSet.count(i) == 0)
      gpuDataArgIndices.push_back(i);
  }
  gpuDataArgCount = (int)gpuDataArgIndices.size();
  for (int di = 0; di < gpuDataArgCount; di++) {
    gpuAvCache[di] =
        static_cast<ArrayDataContainerValue *>(
            methodCallingOriginalPlaceHolderAddrs[gpuDataArgIndices[di]])
            ->arrayValue;
  }
}

void GPUFunctionCommandRE::runDispatchDiagnostics() {
  if (gpuErr != CL_SUCCESS)
    return;

  static std::atomic<int> rjDispatchCounter{0};
  int rjDisp = rjDispatchCounter.fetch_add(1);
  const char *kn = gpuKernelName.c_str();
  bool rjFull = rjDisp <= 12; // layer 0 (+ first kernel of layer 1)
  bool rjResidual =
      (strstr(kn, "rmsnorm") || strstr(kn, "residual")) && rjDisp < 400;
  if (!rjFull && !rjResidual)
    return;

  clFinish(s_clCtx.queue);
  // Read each ENTIRE buffer (chunked) and compute a bit-exact FNV-1a hash
  // over the raw bytes plus sum/maxabs/zeros.
  static std::vector<float> rjChunk(1u << 16); // 64K floats = 256 KB
  for (int di = 0; di < gpuDataArgCount; di++) {
    if (!rjFull && di != 0)
      break; // residual trace: only arg0
    cl_mem buf = (cl_mem)gpuAvCache[di]->gpuBuffer;
    size_t bufBytes = gpuAvCache[di]->gpuBufferBytes;
    if (!buf || bufBytes == 0)
      continue;
    size_t nFloats = bufBytes / sizeof(float);
    if (nFloats == 0)
      continue;
    uint32_t rjHash = 2166136261u; // FNV-1a offset basis
    double rjSum = 0.0;
    float rjMax = 0.0f, rjS0 = 0.0f;
    size_t rjZeros = 0;
    int rjNan = 0;
    bool rjReadOk = true;
    for (size_t off = 0; off < nFloats; off += rjChunk.size()) {
      size_t n = nFloats - off;
      if (n > rjChunk.size())
        n = rjChunk.size();
      if (clEnqueueReadBuffer(s_clCtx.queue, buf, CL_TRUE,
                              off * sizeof(float), n * sizeof(float),
                              rjChunk.data(), 0, nullptr,
                              nullptr) != CL_SUCCESS) {
        rjReadOk = false;
        break;
      }
      const unsigned char *bytes =
          reinterpret_cast<const unsigned char *>(rjChunk.data());
      size_t nBytes = n * sizeof(float);
      for (size_t b = 0; b < nBytes; b++) {
        rjHash ^= bytes[b];
        rjHash *= 16777619u;
      }
      for (size_t j = 0; j < n; j++) {
        float v = rjChunk[j];
        if (off == 0 && j == 0)
          rjS0 = v;
        if (std::isnan(v) || std::isinf(v)) {
          rjNan = 1;
          continue;
        }
        if (v == 0.0f)
          rjZeros++;
        rjSum += v;
        float a = std::fabs(v);
        if (a > rjMax)
          rjMax = a;
      }
    }
    if (!rjReadOk)
      continue;
    RJ_GPU_LOG("[GPU-CHK] disp=%d k=%s arg=%d n=%zu hash=%08x sum=%.9g "
               "maxabs=%g zeros=%zu s0=%g nan=%d\n",
               rjDisp, kn, di, nFloats, rjHash, rjSum, rjMax, rjZeros, rjS0,
               rjNan);
  }

  // Recompute C[row=0,col=0] in double precision from the GPU's own inputs
  // and compare it to the kernel output.
  if (rjFull && strstr(kn, "matmul") && gpuDataArgCount >= 5 &&
      gpuAvCache[0]->gpuBuffer && gpuAvCache[1]->gpuBuffer &&
      gpuAvCache[2]->gpuBuffer && gpuAvCache[3]->gpuBuffer &&
      gpuAvCache[4]->gpuBuffer) {
    float kpar[3] = {0, 0, 0};
    bool ok = clEnqueueReadBuffer(s_clCtx.queue,
                                  (cl_mem)gpuAvCache[4]->gpuBuffer, CL_TRUE, 0,
                                  3 * sizeof(float), kpar, 0, nullptr,
                                  nullptr) == CL_SUCCESS;
    int Kpack = (int)kpar[2];
    size_t needA = (size_t)Kpack * 6;
    size_t haveA = gpuAvCache[0]->gpuBufferBytes / sizeof(float);
    size_t haveW = gpuAvCache[1]->gpuBufferBytes / sizeof(float);
    if (ok && Kpack > 0 && (size_t)Kpack <= haveW && needA <= haveA) {
      float sc = 0.0f, gpuC0 = 0.0f;
      std::vector<float> Wv(Kpack), Av(needA);
      ok &= clEnqueueReadBuffer(s_clCtx.queue, (cl_mem)gpuAvCache[2]->gpuBuffer,
                                CL_TRUE, 0, sizeof(float), &sc, 0, nullptr,
                                nullptr) == CL_SUCCESS;
      ok &= clEnqueueReadBuffer(s_clCtx.queue, (cl_mem)gpuAvCache[1]->gpuBuffer,
                                CL_TRUE, 0, Kpack * sizeof(float), Wv.data(), 0,
                                nullptr, nullptr) == CL_SUCCESS;
      ok &= clEnqueueReadBuffer(s_clCtx.queue, (cl_mem)gpuAvCache[0]->gpuBuffer,
                                CL_TRUE, 0, needA * sizeof(float), Av.data(), 0,
                                nullptr, nullptr) == CL_SUCCESS;
      ok &= clEnqueueReadBuffer(s_clCtx.queue, (cl_mem)gpuAvCache[3]->gpuBuffer,
                                CL_TRUE, 0, sizeof(float), &gpuC0, 0, nullptr,
                                nullptr) == CL_SUCCESS;
      if (ok) {
        double scale = sc;
        double s = 0.0;
        for (int kp = 0; kp < Kpack; kp++) {
          double packed = Wv[kp];
          double w5 = std::floor(packed / 1048576.0);
          packed -= w5 * 1048576.0;
          double w4 = std::floor(packed / 65536.0);
          packed -= w4 * 65536.0;
          double w3 = std::floor(packed / 4096.0);
          packed -= w3 * 4096.0;
          double w2 = std::floor(packed / 256.0);
          packed -= w2 * 256.0;
          double w1 = std::floor(packed / 16.0);
          packed -= w1 * 16.0;
          double w0 = packed;
          w0 = (w0 - 8.0) * scale;
          w1 = (w1 - 8.0) * scale;
          w2 = (w2 - 8.0) * scale;
          w3 = (w3 - 8.0) * scale;
          w4 = (w4 - 8.0) * scale;
          w5 = (w5 - 8.0) * scale;
          size_t ab = (size_t)kp * 6;
          s += Av[ab] * w0 + Av[ab + 1] * w1 + Av[ab + 2] * w2 +
               Av[ab + 3] * w3 + Av[ab + 4] * w4 + Av[ab + 5] * w5;
        }
        RJ_GPU_LOG("[GPU-REF] disp=%d k=%s Kpack=%d C0_gpu=%g C0_ref=%g "
                   "diff=%g\n",
                   rjDisp, kn, Kpack, gpuC0, (double)s, (double)s - gpuC0);
      }
    }
  }
}

RuleEngineInputUnits *GPUFunctionCommandRE::process() {
  // -- global_work_size from calling-context scalar values at parallelism arg
  // positions -- read via pointers cached once in setFields().
  gpuZeroWorkSize = false;
  for (cl_uint pi = 0; pi < gpuWorkDim; pi++) {
    gpuGlobalWorkSize[pi] = (size_t)(*gpuWorkSizeValuePtr[pi]);
    if (gpuGlobalWorkSize[pi] == 0) {
      gpuZeroWorkSize = true;
    }
  }

  // Bind each data argument's existing GPU buffer. Allocation/upload is no
  // longer this function's job — it belongs entirely to LOAD_MEM (first
  // upload / re-upload after RELEASE_MEM) and GPU_LOAD (re-upload after the
  // host mutates an already-resident array). A null buffer here means the
  // script is missing a required LOAD_MEM(array) call.
  gpuBufferError = false;

  for (int di = 0; di < gpuDataArgCount; di++) {
    cl_mem buf = (cl_mem)gpuAvCache[di]->gpuBuffer;
    // if (buf == nullptr) {
    //   RJ_GPU_LOG("[GPU] arg %d has no GPU buffer for kernel '%s' — missing "
    //              "LOAD_MEM(array) before first use\n",
    //              di, gpuKernelName.c_str());
    //   gpuBufferError = true;
    //   break;
    // }
    //gpuSetErr =
        clSetKernelArg(gpuKernel, (cl_uint)di, sizeof(cl_mem), &buf);
    // if (gpuSetErr != CL_SUCCESS) {
    //   RJ_GPU_LOG("[GPU] clSetKernelArg arg=%d failed err=%d\n", di, gpuSetErr);
    //   gpuBufferError = true;
    //   break;
    // }
  }

  // Optional dispatch-state diagnostics; enable when investigating skipped
  // kernels.
  // rjGpuLogDispatchState(gpuZeroWorkSize, gpuBufferError);

  if (!gpuBufferError && !gpuZeroWorkSize) {
#ifdef __ANDROID__
    // Adreno optimization: only for 1-D kernels. Start from wave=64 (a good
    // Adreno wavefront size) and halve it until it both divides evenly into
    // gpuGlobalWorkSize[0] and fits within this kernel's real
    // CL_KERNEL_WORK_GROUP_SIZE cap (gpuMaxWorkGroupSize, queried once in
    // setFields() so we never force a group size the driver would reject).
    // Apple/desktop drivers already pick a good default (nullptr local size),
    // so this whole block is Android-only.
    if (gpuWorkDim == 1) {
        size_t cap = gpuMaxWorkGroupSize > 0 ? gpuMaxWorkGroupSize : 1;
        size_t wave = 64;
        while (wave > 1 && (wave > cap || gpuGlobalWorkSize[0] % wave != 0)) {
            wave >>= 1;
        }
        gpuLocalWorkSize[0] = wave;
        gpuLocalWorkSizePtr = gpuLocalWorkSize;
    }
#endif

    // gpuErr =
        clEnqueueNDRangeKernel(s_clCtx.queue, gpuKernel, gpuWorkDim, nullptr,
                               gpuGlobalWorkSize, gpuLocalWorkSizePtr, 0, nullptr, nullptr);

    // if (gpuErr != CL_SUCCESS) {
    //   RJ_GPU_LOG("[GPU] clEnqueueNDRangeKernel failed: %d\n", gpuErr);
    // }

    // Optional full-buffer trace and matmul oracle; enable for GPU debugging.
    // runDispatchDiagnostics();
  }

  return nextUnit;
}
#endif // GPU_ENABLED

/**
 * Simplified field setup for built-in functions.
 *
 * Built-in functions have a streamlined setup process since they don't require
 * the complex parameter mapping and local variable management of user-defined
 * functions. This method sets up direct access to arguments for efficient
 * built-in function execution.
 *
 * @param map Global map containing rule engine objects for argument resolution
 */
void BuiltInFunctionsImpl::setFields(
    std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
  std::list<DataContainerValue *> methodArgDataContainerAddrList;

  /**
   * ARGUMENT CATEGORIZATION FOR BUILT-IN FUNCTIONS:
   * Collect all argument data container addresses for unified access.
   * Also count variable and array arguments for potential specialized handling.
   */
  for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
    AbstractDataContainer *arg = dynamic_cast<AbstractDataContainer *>(
        map->at(functionCommandInfo->arguments[i]));
    methodArgDataContainerAddrList.push_back(arg->valPtr);

    if (dynamic_cast<ArrayRE *>(map->at(functionCommandInfo->arguments[i])) !=
        nullptr) {
      arrCount++;
    } else {
      varCount++;
    }
  }

  /**
   * ALLOCATE UNIFIED DATA CONTAINER ACCESS ARRAY:
   * Create array for direct argument access using the unified
   * DataContainerValue approach.
   */
  methodArgDataContainerAddr =
      new DataContainerValue *[functionCommandInfo->argumentsSize];

  /**
   * POPULATE UNIFIED DATA CONTAINER ACCESS:
   * Store all argument data container addresses for direct access by built-in
   * functions.
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
RuleEngineInputUnits *NINF::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DataContainerValue *dataContainerValue = methodArgDataContainerAddr[0];

    // Check if this is a variable (DoublePtr)
    if (DoublePtr *doublePtr = dynamic_cast<DoublePtr *>(dataContainerValue)) {
      doublePtr->value = -std::numeric_limits<double>::infinity();
    }
    // Check if this is an array (ArrayValue)
    else if (ArrayDataContainerValue *arrayDataContainerValue =
                 dynamic_cast<ArrayDataContainerValue *>(dataContainerValue)) {
      auto arrayValue = arrayDataContainerValue->arrayValue;
      for (int i = 0; i < arrayValue->totalSize; i++) {
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
RuleEngineInputUnits *PINF::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DataContainerValue *dataContainerValue = methodArgDataContainerAddr[0];

    // Check if this is a variable (DoublePtr)
    if (DoublePtr *doublePtr = dynamic_cast<DoublePtr *>(dataContainerValue)) {
      doublePtr->value = std::numeric_limits<double>::infinity();
    }
    // Check if this is an array (ArrayValue)
    else if (ArrayDataContainerValue *arrayDataContainerValue =
                 dynamic_cast<ArrayDataContainerValue *>(dataContainerValue)) {
      auto arrayValue = arrayDataContainerValue->arrayValue;
      for (int i = 0; i < arrayValue->totalSize; i++) {
        arrayValue->val[i] = std::numeric_limits<float>::infinity();
      }
    }
  }
  return nextUnit;
}

// Static random number generation components for RAND function
static std::random_device rd;  // Non-deterministic random seed
static std::mt19937 gen(rd()); // Mersenne Twister generator engine
static std::uniform_real_distribution<>
    dis(0.0, 1.0); // Uniform distribution [0.0, 1.0)

/**
 * RAND (Random Number Generation) Built-in Function Implementation.
 *
 * Generates random numbers in the range [0.0, 1.0) using the Mersenne Twister
 * algorithm. Supports both single variable and single array arguments.
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
RuleEngineInputUnits *RAND::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DataContainerValue *dataContainerValue = methodArgDataContainerAddr[0];

    // Check if this is a variable (DoublePtr)
    if (DoublePtr *doublePtr = dynamic_cast<DoublePtr *>(dataContainerValue)) {
      doublePtr->value = dis(gen);
    }
    // Check if this is an array (ArrayValue)
    else if (ArrayDataContainerValue *pArrayDataContainerValueValue =
                 dynamic_cast<ArrayDataContainerValue *>(dataContainerValue)) {
      ArrayValue *arrayValue = pArrayDataContainerValueValue->arrayValue;
      for (int i = 0; i < arrayValue->totalSize; i++) {
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
RuleEngineInputUnits *ABS::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *SIN::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *COS::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *TAN::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    doublePtr->value = std::tan(doublePtr->value);
  }
  return nextUnit;
}

// ==================== INVERSE TRIGONOMETRIC BUILT-IN FUNCTIONS
// ====================

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
RuleEngineInputUnits *ASIN::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *ACOS::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *ATAN::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    doublePtr->value = std::atan(doublePtr->value);
  }
  return nextUnit;
}

// ==================== ROUNDING AND CEILING BUILT-IN FUNCTIONS
// ====================

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
RuleEngineInputUnits *FLOOR::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *CEIL::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    doublePtr->value = std::ceil(doublePtr->value);
  }
  return nextUnit;
}

// ==================== EXPONENTIAL AND POWER BUILT-IN FUNCTIONS
// ====================

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
RuleEngineInputUnits *EXP::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *LOG::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
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
RuleEngineInputUnits *SQRT::process() {
  if (functionCommandInfo->argumentsSize >= 1) {
    DoublePtr *doublePtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    doublePtr->value = std::sqrt(doublePtr->value);
  }
  return nextUnit;
}

/**
 * POW (Power Function) Built-in Function Implementation.
 *
 * Computes the first argument raised to the power of the second argument
 * (base^exponent). Requires exactly two variable arguments and modifies the
 * first with the result.
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
RuleEngineInputUnits *POW::process() {
  if (functionCommandInfo->argumentsSize >= 2) {
    DoublePtr *doublePtr1 =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    DoublePtr *doublePtr2 =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[1]);
    doublePtr1->value = std::pow(doublePtr1->value, doublePtr2->value);
  }
  return nextUnit;
}

RuleEngineInputUnits *PACKED_NIBBLE::process() {
  if (functionCommandInfo->argumentsSize >= 2) {
    DoublePtr *packedPtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[0]);
    DoublePtr *indexPtr =
        static_cast<DoublePtr *>(methodArgDataContainerAddr[1]);
    unsigned int packed = static_cast<unsigned int>(packedPtr->value);
    unsigned int index = static_cast<unsigned int>(indexPtr->value);
    packedPtr->value = static_cast<double>((packed >> (index * 4U)) & 15U);
  }
  return nextUnit;
}

RuleEngineInputUnits *GPU_SYNC::process() {
#ifdef GPU_ENABLED
  if (targetArray != nullptr) {
    // ArrayRE::arrayValue is ArrayDataContainerValue; its .arrayValue field is
    // the ArrayValue*
    ArrayValue *arrayValue = targetArray->arrayValue.arrayValue;
    if (arrayValue != nullptr && arrayValue->gpuBuffer != nullptr) {
      clEnqueueReadBuffer(s_clCtx.queue, (cl_mem)arrayValue->gpuBuffer, CL_TRUE,
                          0, arrayValue->gpuBufferBytes, arrayValue->val, 0,
                          nullptr, nullptr);
    }
  }
#endif
  return nextUnit;
}

RuleEngineInputUnits *GPU_LOAD::process() {
#ifdef GPU_ENABLED
  if (targetArray != nullptr) {
    ArrayValue *arrayValue = targetArray->arrayValue.arrayValue;
    if (arrayValue != nullptr && arrayValue->gpuBuffer != nullptr) {
      // Upload host array → GPU buffer (blocking write ensures visibility
      // to all subsequent GPU kernels in the same command queue).
        cl_int err = clEnqueueWriteBuffer(
          s_clCtx.queue, (cl_mem)arrayValue->gpuBuffer, CL_TRUE, 0,
          arrayValue->gpuBufferBytes, arrayValue->val, 0, nullptr, nullptr);
      if (err != CL_SUCCESS)
        RJ_GPU_LOG("[GPU_LOAD] write failed: %d\n", err);
      // Optional transfer diagnostics; enable when investigating GPU_LOAD.
      // rjGpuLogLoadDiagnostics(arrayValue, true, err);
    } else {
      // Optional transfer diagnostics; enable when investigating GPU_LOAD.
      // rjGpuLogLoadDiagnostics(arrayValue, false, CL_SUCCESS);
    }
  }
#endif
  return nextUnit;
}

RuleEngineInputUnits *RELEASE_MEM::process() {
#ifdef GPU_ENABLED
  if (targetArray != nullptr) {
    ArrayValue *arrayValue = targetArray->arrayValue.arrayValue;
    if (arrayValue != nullptr && arrayValue->gpuBuffer != nullptr) {
      // GPU kernel dispatch is async/non-blocking (see GPU_SYNC design), so
      // the kernels that read this buffer may still be in flight. Drain the
      // queue first — releasing an in-use buffer here would free memory the
      // GPU is still reading, corrupting results (e.g. garbage output).
      clFinish(s_clCtx.queue);
      clReleaseMemObject((cl_mem)arrayValue->gpuBuffer);
      arrayValue->gpuBuffer = nullptr;
      arrayValue->gpuBufferBytes = 0;
    }
  }
#endif
  return nextUnit;
}

RuleEngineInputUnits *LOAD_MEM::process() {
#ifdef GPU_ENABLED
  if (targetArray != nullptr) {
    ArrayValue *arrayValue = targetArray->arrayValue.arrayValue;
    if (arrayValue != nullptr && arrayValue->gpuBuffer == nullptr) {
      s_clCtx.init();
      if (!s_clCtx.available) {
        RJ_GPU_LOG("[LOAD_MEM] OpenCL unavailable – skipped\n");
        return nextUnit;
      }
      size_t needed = (size_t)arrayValue->totalSize * sizeof(float);
      cl_mem_flags flags = CL_MEM_READ_WRITE;
#ifdef __ANDROID__
      // See the matching comment in GPUFunctionCommandRE::process(): Android
      // GPU drivers don't give true zero-copy for CL_MEM_USE_HOST_PTR, so
      // always do a real copy here to avoid stale/corrupt reads.
      flags |= CL_MEM_COPY_HOST_PTR;
#else
      flags |= arrayValue->isBinaryLoaded ? CL_MEM_USE_HOST_PTR
                                          : CL_MEM_COPY_HOST_PTR;
#endif
      cl_int err;
      cl_mem buf = clCreateBuffer(s_clCtx.context, flags, needed,
                                  arrayValue->val, &err);
      if (err != CL_SUCCESS) {
        RJ_GPU_LOG("[LOAD_MEM] clCreateBuffer failed: %d\n", err);
        return nextUnit;
      }
      arrayValue->gpuBuffer = buf;
      arrayValue->gpuBufferBytes = needed;
    }
  }
#endif
  return nextUnit;
}