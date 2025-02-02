if [ "$#" -ne 1 ]; then
  echo "Usage: $0 /path/to/ndk"
  exit 1
fi

ANDROID_NDK=$1

for ABI in arm64-v8a x86_64; do  #armeabi-v7a x86 x86_64; do
  JSONCPP_INCLUDE_DIRS=/Users/pranav/Library/Android/sdk/ndk/26.1.10909125/includes/jsoncpp/build/$ABI/include
  JSONCPP_LIBRARY_DIRS=/Users/pranav/Library/Android/sdk/ndk/26.1.10909125/includes/jsoncpp/build/$ABI/lib
  JSONCPP_LIBRARIES=$JSONCPP_LIBRARY_DIRS/libjsoncpp.so

  echo "Building for ABI: $ABI"
  echo "JSONCPP_INCLUDE_DIRS: $JSONCPP_INCLUDE_DIRS"
  echo "JSONCPP_LIBRARY_DIRS: $JSONCPP_LIBRARY_DIRS"
  echo "JSONCPP_LIBRARIES: $JSONCPP_LIBRARIES"

  cmake -H. -Bbuild/$ABI \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-21 \
    -DJSONCPP_INCLUDE_DIRS=$JSONCPP_INCLUDE_DIRS \
    -DJSONCPP_LIBRARY_DIRS=$JSONCPP_LIBRARY_DIRS \
    -DJSONCPP_LIBRARIES=$JSONCPP_LIBRARIES

  cmake --build build/$ABI --target native
done