#pragma once
// ----------------------------------------------------------------------------
// Android-only runtime OpenCL loader.
//
// On Android the Khronos ICD loader finds OpenCL via /etc/OpenCL/vendors/*.icd
// files, which do not exist on Android.  The vendor's libOpenCL.so lives at a
// device-specific path (e.g. /system/vendor/lib64/libOpenCL.so).  We must
// dlopen it explicitly.
//
// Usage
//   Before calling any cl* function, call openclLoad().
//   It is idempotent and thread-safe; call it once from GpuContext::init().
//   Returns true if the library was found and all symbols resolved.
//
// The AndroidManifest must also declare:
//   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
// so the system linker namespace grants us access to the vendor library.
// ----------------------------------------------------------------------------

#ifdef __ANDROID__
#include <android/log.h>
#include <dlfcn.h>

#define _RJ_OCL_TAG "RamanujanOpenCL"
#define _RJ_OCL_LOGI(...)                                                      \
  __android_log_print(ANDROID_LOG_INFO, _RJ_OCL_TAG, __VA_ARGS__)
#define _RJ_OCL_LOGE(...)                                                      \
  __android_log_print(ANDROID_LOG_ERROR, _RJ_OCL_TAG, __VA_ARGS__)

// ── function pointer types ────────────────────────────────────────────────
typedef cl_int (*PFN_clGetPlatformIDs)(cl_uint, cl_platform_id *, cl_uint *);
typedef cl_int (*PFN_clGetDeviceIDs)(cl_platform_id, cl_device_type, cl_uint,
                                     cl_device_id *, cl_uint *);
typedef cl_context (*PFN_clCreateContext)(
    const cl_context_properties *, cl_uint, const cl_device_id *,
    void (*)(const char *, const void *, size_t, void *), void *, cl_int *);
typedef cl_command_queue (*PFN_clCreateCommandQueue)(
    cl_context, cl_device_id, cl_command_queue_properties, cl_int *);
typedef cl_command_queue (*PFN_clCreateCommandQueueWithProperties)(
    cl_context, cl_device_id, const cl_queue_properties *, cl_int *);
typedef cl_int (*PFN_clGetDeviceInfo)(cl_device_id, cl_device_info, size_t,
                                      void *, size_t *);
typedef cl_int (*PFN_clGetPlatformInfo)(cl_platform_id, cl_platform_info,
                                        size_t, void *, size_t *);
typedef cl_mem (*PFN_clCreateBuffer)(cl_context, cl_mem_flags, size_t, void *,
                                     cl_int *);
typedef cl_int (*PFN_clEnqueueWriteBuffer)(cl_command_queue, cl_mem, cl_bool,
                                           size_t, size_t, const void *,
                                           cl_uint, const cl_event *,
                                           cl_event *);
typedef cl_int (*PFN_clEnqueueReadBuffer)(cl_command_queue, cl_mem, cl_bool,
                                          size_t, size_t, void *, cl_uint,
                                          const cl_event *, cl_event *);
typedef cl_program (*PFN_clCreateProgramWithSource)(cl_context, cl_uint,
                                                    const char **,
                                                    const size_t *, cl_int *);
typedef cl_int (*PFN_clBuildProgram)(cl_program, cl_uint, const cl_device_id *,
                                     const char *, void (*)(cl_program, void *),
                                     void *);
typedef cl_int (*PFN_clGetProgramBuildInfo)(cl_program, cl_device_id,
                                            cl_program_build_info, size_t,
                                            void *, size_t *);
typedef cl_kernel (*PFN_clCreateKernel)(cl_program, const char *, cl_int *);
typedef cl_int (*PFN_clSetKernelArg)(cl_kernel, cl_uint, size_t, const void *);
typedef cl_int (*PFN_clGetKernelWorkGroupInfo)(cl_kernel, cl_device_id,
                                               cl_kernel_work_group_info,
                                               size_t, void *, size_t *);
typedef cl_int (*PFN_clEnqueueNDRangeKernel)(cl_command_queue, cl_kernel,
                                             cl_uint, const size_t *,
                                             const size_t *, const size_t *,
                                             cl_uint, const cl_event *,
                                             cl_event *);
typedef cl_int (*PFN_clFinish)(cl_command_queue);
typedef cl_int (*PFN_clFlush)(cl_command_queue);
typedef cl_int (*PFN_clReleaseMemObject)(cl_mem);
typedef cl_int (*PFN_clReleaseProgram)(cl_program);
typedef cl_int (*PFN_clReleaseKernel)(cl_kernel);
typedef cl_int (*PFN_clReleaseCommandQueue)(cl_command_queue);
typedef cl_int (*PFN_clReleaseContext)(cl_context);

// ── storage ───────────────────────────────────────────────────────────────
namespace rj_ocl {
static void *g_lib = nullptr;

static PFN_clGetPlatformIDs pfn_clGetPlatformIDs = nullptr;
static PFN_clGetDeviceIDs pfn_clGetDeviceIDs = nullptr;
static PFN_clCreateContext pfn_clCreateContext = nullptr;
static PFN_clCreateCommandQueue pfn_clCreateCommandQueue = nullptr;
static PFN_clCreateCommandQueueWithProperties
    pfn_clCreateCommandQueueWithProperties = nullptr;
static PFN_clGetDeviceInfo pfn_clGetDeviceInfo = nullptr;
static PFN_clGetPlatformInfo pfn_clGetPlatformInfo = nullptr;
static PFN_clCreateBuffer pfn_clCreateBuffer = nullptr;
static PFN_clEnqueueWriteBuffer pfn_clEnqueueWriteBuffer = nullptr;
static PFN_clEnqueueReadBuffer pfn_clEnqueueReadBuffer = nullptr;
static PFN_clCreateProgramWithSource pfn_clCreateProgramWithSource = nullptr;
static PFN_clBuildProgram pfn_clBuildProgram = nullptr;
static PFN_clGetProgramBuildInfo pfn_clGetProgramBuildInfo = nullptr;
static PFN_clCreateKernel pfn_clCreateKernel = nullptr;
static PFN_clSetKernelArg pfn_clSetKernelArg = nullptr;
static PFN_clGetKernelWorkGroupInfo pfn_clGetKernelWorkGroupInfo = nullptr;
static PFN_clEnqueueNDRangeKernel pfn_clEnqueueNDRangeKernel = nullptr;
static PFN_clFinish pfn_clFinish = nullptr;
static PFN_clFlush pfn_clFlush = nullptr;
static PFN_clReleaseMemObject pfn_clReleaseMemObject = nullptr;
static PFN_clReleaseProgram pfn_clReleaseProgram = nullptr;
static PFN_clReleaseKernel pfn_clReleaseKernel = nullptr;
static PFN_clReleaseCommandQueue pfn_clReleaseCommandQueue = nullptr;
static PFN_clReleaseContext pfn_clReleaseContext = nullptr;

#define _RJ_LOAD(name)                                                         \
  pfn_##name = reinterpret_cast<PFN_##name>(dlsym(g_lib, #name));              \
  if (!pfn_##name) {                                                           \
    _RJ_OCL_LOGE("dlsym failed: " #name);                                      \
    return false;                                                              \
  }

static bool load() {
  if (g_lib)
    return true;

  // Try common vendor paths; the manifest <uses-native-library> tag makes
  // "libOpenCL.so" resolvable via the system linker namespace on Android 10+.
  const char *candidates[] = {
      "libOpenCL.so", "/system/vendor/lib64/libOpenCL.so",
      "/system/lib64/libOpenCL.so", "/vendor/lib64/libOpenCL.so", nullptr};
  for (int i = 0; candidates[i]; i++) {
    g_lib = dlopen(candidates[i], RTLD_NOW | RTLD_GLOBAL);
    if (g_lib) {
      _RJ_OCL_LOGI("Loaded OpenCL from: %s", candidates[i]);
      break;
    }
  }
  if (!g_lib) {
    _RJ_OCL_LOGE("Could not load libOpenCL.so: %s", dlerror());
    return false;
  }

  _RJ_LOAD(clGetPlatformIDs)
  _RJ_LOAD(clGetDeviceIDs)
  _RJ_LOAD(clCreateContext)
  _RJ_LOAD(clCreateCommandQueue)
  // clCreateCommandQueueWithProperties is CL 2.0+; not all devices support it.
  // Resolve best-effort — GpuContext::init() falls back to clCreateCommandQueue
  // when this pointer is null.
  pfn_clCreateCommandQueueWithProperties =
      reinterpret_cast<PFN_clCreateCommandQueueWithProperties>(
          dlsym(g_lib, "clCreateCommandQueueWithProperties"));
  // (no fatal error if absent)
  _RJ_LOAD(clGetDeviceInfo)
  _RJ_LOAD(clGetPlatformInfo)
  _RJ_LOAD(clCreateBuffer)
  _RJ_LOAD(clEnqueueWriteBuffer)
  _RJ_LOAD(clEnqueueReadBuffer)
  _RJ_LOAD(clCreateProgramWithSource)
  _RJ_LOAD(clBuildProgram)
  _RJ_LOAD(clGetProgramBuildInfo)
  _RJ_LOAD(clCreateKernel)
  _RJ_LOAD(clSetKernelArg)
  _RJ_LOAD(clGetKernelWorkGroupInfo)
  _RJ_LOAD(clEnqueueNDRangeKernel)
  _RJ_LOAD(clFinish)
  _RJ_LOAD(clFlush)
  _RJ_LOAD(clReleaseMemObject)
  _RJ_LOAD(clReleaseProgram)
  _RJ_LOAD(clReleaseKernel)
  _RJ_LOAD(clReleaseCommandQueue)
  _RJ_LOAD(clReleaseContext)

  _RJ_OCL_LOGI("OpenCL symbols resolved successfully");
  return true;
}
} // namespace rj_ocl

// Redirect bare cl* names to the loaded function pointers
#define clGetPlatformIDs rj_ocl::pfn_clGetPlatformIDs
#define clGetDeviceIDs rj_ocl::pfn_clGetDeviceIDs
#define clCreateContext rj_ocl::pfn_clCreateContext
#define clCreateCommandQueue rj_ocl::pfn_clCreateCommandQueue
#define clCreateCommandQueueWithProperties                                     \
  rj_ocl::pfn_clCreateCommandQueueWithProperties
#define clGetDeviceInfo rj_ocl::pfn_clGetDeviceInfo
#define clGetPlatformInfo rj_ocl::pfn_clGetPlatformInfo
#define clCreateBuffer rj_ocl::pfn_clCreateBuffer
#define clEnqueueWriteBuffer rj_ocl::pfn_clEnqueueWriteBuffer
#define clEnqueueReadBuffer rj_ocl::pfn_clEnqueueReadBuffer
#define clCreateProgramWithSource rj_ocl::pfn_clCreateProgramWithSource
#define clBuildProgram rj_ocl::pfn_clBuildProgram
#define clGetProgramBuildInfo rj_ocl::pfn_clGetProgramBuildInfo
#define clCreateKernel rj_ocl::pfn_clCreateKernel
#define clSetKernelArg rj_ocl::pfn_clSetKernelArg
#define clGetKernelWorkGroupInfo rj_ocl::pfn_clGetKernelWorkGroupInfo
#define clEnqueueNDRangeKernel rj_ocl::pfn_clEnqueueNDRangeKernel
#define clFinish rj_ocl::pfn_clFinish
#define clFlush rj_ocl::pfn_clFlush
#define clReleaseMemObject rj_ocl::pfn_clReleaseMemObject
#define clReleaseProgram rj_ocl::pfn_clReleaseProgram
#define clReleaseKernel rj_ocl::pfn_clReleaseKernel
#define clReleaseCommandQueue rj_ocl::pfn_clReleaseCommandQueue
#define clReleaseContext rj_ocl::pfn_clReleaseContext

// Convenience wrapper — call this once from GpuContext::init()
static inline bool openclLoad() { return rj_ocl::load(); }

#else  // not Android
static inline bool openclLoad() { return true; } // no-op; link-time OpenCL
#endif // __ANDROID__
