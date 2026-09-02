cmake_minimum_required(VERSION 3.27)
project(native)

set(CMAKE_CXX_STANDARD 14)

if(NOT CMAKE_BUILD_TYPE)
  # CACHE FORCE is required here: a plain set() only affects this configure
  # run's in-memory value and does not persist CMAKE_BUILD_TYPE into
  # CMakeCache.txt, so a Makefile-generator build silently compiles with no
  # per-config flags at all (no -O3/-DNDEBUG) even though this line "sets"
  # Release. That left the interpreter running at debug speed.
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()
if(MSVC)
    add_compile_options($<$<CONFIG:Release>:/O2>)
else()
    add_compile_options($<$<CONFIG:Release>:-O3>)
endif()

include_directories(input)
include_directories(processing)

include(FetchContent)

# ---------------------------------------------------------------------------
# Protobuf
# ---------------------------------------------------------------------------
set(protobuf_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(protobuf_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(protobuf_WITH_ZLIB OFF CACHE BOOL "" FORCE)
set(protobuf_MSVC_STATIC_RUNTIME OFF CACHE BOOL "" FORCE)
if(CMAKE_CROSSCOMPILING AND NOT DEFINED protobuf_BUILD_PROTOC_BINARIES)
    set(protobuf_BUILD_PROTOC_BINARIES OFF CACHE BOOL "" FORCE)
endif()
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_Declare(
    protobuf
    GIT_REPOSITORY https://github.com/protocolbuffers/protobuf.git
    GIT_TAG        v3.25.3
)
FetchContent_MakeAvailable(protobuf)

# Generate C++ sources from the proto file shared with the Java project.
set(PROTO_FILE "${CMAKE_CURRENT_SOURCE_DIR}/../../rule-engine/src/main/proto/rule_engine_input.proto")
set(PROTO_OUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/proto")
file(MAKE_DIRECTORY ${PROTO_OUT_DIR})

if(PROTOC_EXECUTABLE)
    set(PROTOC_COMMAND "${PROTOC_EXECUTABLE}")
else()
    set(PROTOC_COMMAND protobuf::protoc)
endif()

add_custom_command(
    OUTPUT ${PROTO_OUT_DIR}/rule_engine_input.pb.cc ${PROTO_OUT_DIR}/rule_engine_input.pb.h
    COMMAND ${PROTOC_COMMAND}
    ARGS --cpp_out=${PROTO_OUT_DIR} --proto_path=${CMAKE_CURRENT_SOURCE_DIR}/../../rule-engine/src/main/proto ${PROTO_FILE}
    DEPENDS ${PROTO_FILE}
    COMMENT "Generating C++ protobuf files"
)

set(PROTO_SRCS "${PROTO_OUT_DIR}/rule_engine_input.pb.cc")
set(PROTO_HDRS "${PROTO_OUT_DIR}/rule_engine_input.pb.h")
include_directories(${PROTO_OUT_DIR})

# ---------------------------------------------------------------------------
# GPU support  (disabled by default – no OpenCL dependency for standard builds)
# Enable with:  cmake -DGPU_ENABLED=ON ..
# macOS:  OpenCL ships with the system framework, no extra install needed.
# Linux:  sudo apt install ocl-icd-opencl-dev opencl-headers
# Windows: install GPU vendor drivers (NVIDIA CUDA Toolkit / AMD ROCm / Intel OpenCL SDK)
# ---------------------------------------------------------------------------
option(GPU_ENABLED "Build with OpenCL GPU kernel execution support" ON)
if(GPU_ENABLED)
    if(APPLE)
        # macOS ships OpenCL as a system framework (-framework OpenCL).
        # The Khronos ICD loader is for Linux/Windows; it does NOT discover
        # Apple's platform and returns 0 platforms on macOS (including Apple Silicon).
        find_package(OpenCL REQUIRED)
        message(STATUS "[Ramanujan] OpenCL GPU support ENABLED via system framework  (macOS, version ${OpenCL_VERSION_STRING})")
    elseif(ANDROID)
        # On Android we dlopen libOpenCL.so at runtime (see opencl_loader.h).
        # The Khronos ICD loader looks for /etc/OpenCL/vendors/*.icd which does
        # NOT exist on Android, so clGetPlatformIDs always returns 0 platforms.
        # Instead we only fetch the headers for compilation and rely on dlopen
        # at runtime — no ICD loader is linked.
        FetchContent_Declare(
            OpenCLHeaders
            GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
            GIT_TAG        v2023.04.17
        )
        FetchContent_MakeAvailable(OpenCLHeaders)
        message(STATUS "[Ramanujan] OpenCL GPU support ENABLED via dlopen (Android) – headers only, no ICD loader")
    else()
        # Linux / Windows: compile the Khronos ICD loader from source.
        # On Linux: sudo apt install ocl-icd-opencl-dev opencl-headers (or let
        # FetchContent build it from source as done here).
        FetchContent_Declare(
            OpenCLHeaders
            GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
            GIT_TAG        v2023.04.17
        )
        FetchContent_MakeAvailable(OpenCLHeaders)

        # ICD loader must know where the headers live before it is configured
        set(OPENCL_ICD_LOADER_HEADERS_DIR ${openclheaders_SOURCE_DIR} CACHE PATH "" FORCE)

        FetchContent_Declare(
            OpenCLICDLoader
            GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-ICD-Loader.git
            GIT_TAG        v2023.04.17
        )
        FetchContent_MakeAvailable(OpenCLICDLoader)
        message(STATUS "[Ramanujan] OpenCL GPU support ENABLED via Khronos ICD loader  (Linux/Windows)")
    endif()
    add_compile_definitions(GPU_ENABLED)
else()
    message(STATUS "[Ramanujan] OpenCL GPU support DISABLED  (pass -DGPU_ENABLED=ON to enable)")
endif()
IF(ANDROID)
    # The NDK toolchain sets CMAKE_SYSROOT for every supported host OS.
    include_directories(${CMAKE_SYSROOT}/usr/include)
ELSE()
    find_package(JNI REQUIRED)
    include_directories(${JNI_INCLUDE_DIRS})
ENDIF()

if(NOT WIN32)
    add_definitions(-D_GNU_SOURCE)
endif()

# Core sources shared by the JNI library and standalone test binary
set(CORE_SOURCE_FILES
        "${PROTO_SRCS}"
        "${PROTO_HDRS}"
        input/Array.hpp
        input/ArrayCommand.hpp
        input/Command.hpp
        input/Condition.hpp
        input/Constant.hpp
        input/FunctionCall.hpp
        input/If.hpp
        input/Operation.hpp
        input/RuleEngineInput.hpp
        input/RuleEngineInputUnit.hpp
        input/Variable.hpp
        input/While.hpp
        input/RedefineArrayCommand.hpp
        processing/ProcessingResult.hpp
        processing/Processor.cpp
        processing/Processor.hpp
        ruleEngineObject/RuleEngineInputUnits.hpp
        ruleEngineObject/CommandRE.h
        ruleEngineObject/ArrayCommandRE.h
        ruleEngineObject/dataContainer/ArrayRE.h
        ruleEngineObject/ConditionRE.h
        ruleEngineObject/ConstantRE.h
        ruleEngineObject/FunctionCallRE.h
        ruleEngineObject/FunctionCommandRE.h
        ruleEngineObject/IfRE.h
        ruleEngineObject/OperationRE.h
        ruleEngineObject/ReturnOperationRE.h
        ruleEngineObject/WhileRE.h
        ruleEngineObject/dataContainer/VariableRE.h
        ruleEngineObject/dataContainer/AbstractDataContainer.h
        ruleEngineObject/dataContainer/DataContainerRE.h
        ruleEngineObject/CommandRE.cpp
        ruleEngineObject/RedefineArrayCommandRE.h
        ruleEngineObject/RedefineArrayCommandRE.cpp
        input/Command.cpp
        ruleEngineObject/dataContainer/VaraiableRE.cpp
        input/Variable.cpp
        input/Operation.cpp
        input/ReturnOperation.cpp
        input/Condition.cpp
        input/Constant.cpp
        input/FunctionCall.cpp
        input/If.cpp
        input/While.cpp
        ruleEngineObject/FunctionCommandRE.cpp
        input/Array.cpp
        ruleEngineObject/CommandProcessing.cpp
        ruleEngineObject/CommandProcessing.h
        ruleEngineObject/CommandProcessing.cpp
        ruleEngineObject/datastructure/BooleanWrapper.h
        ruleEngineObject/datastructure/DoubleWrapper.h
        ruleEngineObject/DataOperation.h
        ruleEngineObject/dataContainer/EquationBasedDataOperation.cpp
        ruleEngineObject/dataContainer/EquationBasedDataOperation.h
        processing/operatorFunctioning/CachedOperationFunctioning.h
        ruleEngineObject/OperationRE.cpp
        processing/operatorFunctioning/OperationFunctioning.h
        processing/conditionFunctioning/ConditionFunctioning.h
        processing/conditionFunctioning/CachedConditionFunctioning.h
        ruleEngineObject/ConditionRE.cpp
        processing/operatorFunctioning/AddImpl.h
        processing/operatorFunctioning/AssignImpl.h
        processing/operatorFunctioning/DivideImpl.h
        processing/operatorFunctioning/MultiplyImpl.h
        processing/conditionFunctioning/AndImpl.h
        processing/conditionFunctioning/GreaterThanEqualToImpl.h
        processing/conditionFunctioning/GreaterThanImpl.h
        processing/conditionFunctioning/IsEqualImpl.h
        processing/conditionFunctioning/NotEqualImpl.h
        processing/conditionFunctioning/LessThanEqualToImpl.h
        processing/conditionFunctioning/LessThanImpl.h
        processing/conditionFunctioning/NotImpl.h
        processing/conditionFunctioning/OrImpl.h
        processing/operatorFunctioning/MinusImpl.h
        ruleEngineObject/dataContainer/array/ArrayValue.h
        ruleEngineObject/dataContainer/array/ArrayValDataContainer.h
        in_ramanujan_rule_engine_NativeProcessor.h
        processing/ProcessingResult.cpp
        NativeProcessor.cpp
        ruleEngineObject/dataContainer/array/ArrayValue.cpp
        ruleEngineObject/CommandTypeProcessingDefinition.h
        ruleEngineObject/processingDefinition/ConstantReProcessing.h
        ruleEngineObject/processingDefinition/IfReProcessing.h
        ruleEngineObject/processingDefinition/WhileReProcessing.h
        ruleEngineObject/processingDefinition/OperationReProcessing.h
        ruleEngineObject/processingDefinition/ReturnOperationReProcessing.h
        ruleEngineObject/processingDefinition/VariableReProcessing.h
        ruleEngineObject/processingDefinition/FunctionReProcessing.h
        ruleEngineObject/processingDefinition/ConditionReProcessing.h
        ruleEngineObject/processingDefinition/DefaultProcessing.h
        ruleEngineObject/processingDefinition/RedefineArrayCommandReProcessing.h
        ruleEngineObject/DefaultRuleEngineUnits.h
        ruleEngineObject/dataContainer/DataContainerValue.h
        processing/DebugPoint.h
        processing/DebugPoint.cpp
        ruleEngineObject/dataContainer/DataContainerValueFunctionCommandRE.h
        ruleEngineObject/DataContainerValueFunctionCommandREMemMaintainer.h
        ruleEngineObject/dataContainer/ArrayRE.cpp
)

# Standalone test binary (includes Test.cpp which still uses JSON for dev convenience)
add_executable(native ${CORE_SOURCE_FILES} ruleEngineObject/Test.cpp)
target_link_libraries(native PRIVATE protobuf::libprotobuf)
if(WIN32)
    set_target_properties(native PROPERTIES OUTPUT_NAME "native_test")
endif()

## Production shared-library target (no DEBUG_BUILD – used by the JVM via JNI)
add_library(native_lib SHARED ${CORE_SOURCE_FILES})
target_link_libraries(native_lib PRIVATE protobuf::libprotobuf)
set_target_properties(native_lib PROPERTIES OUTPUT_NAME "native")

## Debug build target
#add_library(native_debug SHARED ${SOURCE_FILES})
#target_link_libraries(native_debug PRIVATE protobuf::libprotobuf)
#set_target_properties(native_debug PROPERTIES COMPILE_FLAGS "-DDEBUG_BUILD")
#set_target_properties(native_debug PROPERTIES OUTPUT_NAME "native_debug")

# Link OpenCL into all three targets when GPU support is enabled.
# On Android we use dlopen at runtime so we only add the headers include path;
# we do NOT link against OpenCL::OpenCL (the ICD loader) to avoid the
# /etc/OpenCL/vendors discovery that doesn't work on Android.
if(GPU_ENABLED)
    if(ANDROID)
        # Headers only — runtime loading via dlopen in opencl_loader.h.
        # Link against libdl so dlopen/dlsym/dlerror resolve.
        target_include_directories(native PRIVATE ${openclheaders_SOURCE_DIR})
        target_link_libraries(native PRIVATE dl log)
        target_include_directories(native_lib PRIVATE ${openclheaders_SOURCE_DIR})
        target_link_libraries(native_lib PRIVATE dl log)
    else()
        target_link_libraries(native PRIVATE OpenCL::OpenCL)
        target_link_libraries(native_lib PRIVATE OpenCL::OpenCL)
    endif()
endif()

#find_package(Boost REQUIRED)
#if(Boost_FOUND)
#    include_directories(${Boost_INCLUDE_DIRS})
#    target_link_libraries(native ${Boost_LIBRARIES})
#    target_link_libraries(native_debug ${Boost_LIBRARIES})
#endif()