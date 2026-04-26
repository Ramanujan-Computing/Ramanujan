if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/ndk"
  exit 1
fi

# =============================================================================
# Android GPU / OpenCL support — how opencl_loader.h gets into the build
# =============================================================================
#
# NO extra copy steps are needed.  The file  native/opencl_loader.h  lives in
# this source tree and is picked up automatically by CMake when it compiles
# ruleEngineObject/FunctionCommandRE.cpp (which does  #include "../opencl_loader.h").
#
# Why the loader is needed on Android
# ------------------------------------
# On Linux and Windows, the Khronos ICD loader finds OpenCL implementations by
# scanning  /etc/OpenCL/vendors/*.icd  files installed by the GPU driver.
# Those files do NOT exist on Android.  As a result, when the ICD loader is
# linked into the binary and  clGetPlatformIDs()  is called, it always returns
# 0 platforms →  GpuContext::available = false  → every GPU kernel silently
# falls through without executing → the position/velocity arrays stay unchanged
# → arrChangeMap() sees no diff from its snapshot → the arrays are absent from
# the result sent back to the homelab server.
#
# How opencl_loader.h fixes this
# --------------------------------
# Instead of linking against the ICD loader (OpenCL::OpenCL), on Android we:
#   1. Fetch only the Khronos OpenCL *headers* (CMakeLists.txt FetchContent) so
#      the GPU code compiles.
#   2. At runtime, opencl_loader.h calls  dlopen("libOpenCL.so")  (+ several
#      vendor fallback paths such as /system/vendor/lib64/libOpenCL.so).
#   3. Every  cl*  symbol is resolved via  dlsym()  and stored in a pfn_* pointer.
#   4. Preprocessor  #define  macros redirect every bare  cl*  call to its pfn_*
#      pointer, so no other source file needs to change.
#   5. GpuContext::init() calls  openclLoad()  before the first  clGetPlatformIDs
#      call, so the vendor's OpenCL is always found.
#   6. CMakeLists.txt links  -ldl  and  -llog  (Android logging) instead of the
#      ICD loader.
#
# AndroidManifest requirement
# ----------------------------
# The dlopen succeeds on Android 10+ only if the app manifest grants the linker
# namespace access to the vendor library:
#
#   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
#
# This line is already present in androidapp/app/src/main/AndroidManifest.xml.
# Without it, dlopen("libOpenCL.so") returns NULL even on devices that ship the
# library, because the linker namespace isolates app libraries from vendor libs.
#
# After building, copy the .so to the Android project
# ----------------------------------------------------
# The build produces:
#   build/arm64-v8a/lib/libnative.so
#   build/x86_64/lib/libnative.so
#
# Copy them to the Android Gradle project so they are packaged into the APK:
#   cp build/arm64-v8a/lib/libnative.so \
#      ../../androidapp/app/src/main/jniLibs/arm64-v8a/libnative.so
#   cp build/x86_64/lib/libnative.so \
#      ../../androidapp/app/src/main/jniLibs/x86_64/libnative.so
#
# Then rebuild the APK in Android Studio (or  ./gradlew assembleDebug  from the
# androidapp/ directory) and reinstall on the device.
# =============================================================================

ANDROID_NDK=$1

for ABI in arm64-v8a x86_64; do  #armeabi-v7a x86 x86_64; do
  JSONCPP_INCLUDE_DIRS=/Users/pranav/Library/Android/sdk/ndk/26.1.10909125/includes/jsoncpp/build/$ABI/include
  JSONCPP_LIBRARY_DIRS=/Users/pranav/Library/Android/sdk/ndk/26.1.10909125/includes/jsoncpp/build/$ABI/lib
  JSONCPP_LIBRARIES=$JSONCPP_LIBRARY_DIRS/libjsoncpp.so

  echo "Building for ABI: $ABI"
  echo "JSONCPP_INCLUDE_DIRS: $JSONCPP_INCLUDE_DIRS"
  echo "JSONCPP_LIBRARY_DIRS: $JSONCPP_LIBRARY_DIRS"
  echo "JSONCPP_LIBRARIES: $JSONCPP_LIBRARIES"

  # Remove stale cache so option changes (GPU_ENABLED) take effect cleanly.
  rm -f build/$ABI/CMakeCache.txt

  cmake -H. -Bbuild/$ABI \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-21 \
    -DGPU_ENABLED=ON \
    -DJSONCPP_INCLUDE_DIRS=$JSONCPP_INCLUDE_DIRS \
    -DJSONCPP_LIBRARY_DIRS=$JSONCPP_LIBRARY_DIRS \
    -DJSONCPP_LIBRARIES=$JSONCPP_LIBRARIES

  cmake --build build/$ABI --target native

  # ── Auto-copy .so into the Android Gradle jniLibs tree ──────────────────
  DEST=../../androidapp/app/src/main/jniLibs/$ABI
  mkdir -p "$DEST"
  cp build/$ABI/lib/libnative.so "$DEST/libnative.so"
  echo "Copied libnative.so → $DEST"
done