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

# =============================================================================
# Locate or build a host protoc compiler for generating C++ protobuf code
# during Android cross-compilation.
# =============================================================================
HOST_PROTOC=""
for candidate in cmake-build-debug/bin/protoc build-gpu/bin/protoc build-host/bin/protoc build-host/_deps/protobuf-build/protoc cmake-build-non-debug/bin/protoc; do
  if [ -x "$candidate" ]; then
    HOST_PROTOC="$(pwd)/$candidate"
    break
  fi
done

if [ -z "$HOST_PROTOC" ]; then
  echo "Building host protoc compiler for cross-compilation..."
  cmake -H. -Bbuild-host -DGPU_ENABLED=OFF
  cmake --build build-host --target protoc
  if [ -x "build-host/bin/protoc" ]; then
    HOST_PROTOC="$(pwd)/build-host/bin/protoc"
  elif [ -x "build-host/_deps/protobuf-build/protoc" ]; then
    HOST_PROTOC="$(pwd)/build-host/_deps/protobuf-build/protoc"
  else
    echo "Error: Could not build or locate host protoc compiler."
    exit 1
  fi
fi
echo "Using host protoc compiler: $HOST_PROTOC"

for ABI in arm64-v8a; do  # armeabi-v7a x86 x86_64
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
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-21 \
    -DGPU_ENABLED=ON \
    -DPROTOC_EXECUTABLE="$HOST_PROTOC" \
    -Dprotobuf_BUILD_PROTOC_BINARIES=OFF \
    -DJSONCPP_INCLUDE_DIRS=$JSONCPP_INCLUDE_DIRS \
    -DJSONCPP_LIBRARY_DIRS=$JSONCPP_LIBRARY_DIRS \
    -DJSONCPP_LIBRARIES=$JSONCPP_LIBRARIES

  cmake --build build/$ABI --target native_lib

  # ── Auto-copy .so into the Android Gradle jniLibs tree ──────────────────
  DEST=../../androidapp/app/src/main/jniLibs/$ABI
  mkdir -p "$DEST"
  if [ -f "build/$ABI/libnative.so" ]; then
    cp build/$ABI/libnative.so "$DEST/libnative.so"
  elif [ -f "build/$ABI/lib/libnative.so" ]; then
    cp build/$ABI/lib/libnative.so "$DEST/libnative.so"
  else
    echo "Error: libnative.so not found in build/$ABI or build/$ABI/lib"
    exit 1
  fi
  echo "Copied libnative.so → $DEST"
done