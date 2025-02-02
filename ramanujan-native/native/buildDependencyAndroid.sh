#!/bin/bash

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 /path/to/ndk /path/to/jsoncpp/source"
  exit 1
fi

ANDROID_NDK=$1
JSONCPP_SRC_DIR=$2
BUILD_DIR=$JSONCPP_SRC_DIR/build

# Create build directory if it doesn't exist
mkdir -p $BUILD_DIR

for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
  cmake -H$JSONCPP_SRC_DIR -B$BUILD_DIR/$ABI \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-21 \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_SHARED_LIBS=ON
  cmake --build $BUILD_DIR/$ABI --target jsoncpp_lib
done
