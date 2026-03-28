cmake_minimum_required(VERSION 3.27)
project(native)

set(CMAKE_CXX_STANDARD 14)

include_directories(input)
include_directories(processing)

include(FetchContent)
set(BUILD_SHARED_LIBS OFF) # Ensure static build
set(JSONCPP_WITH_PIC ON)   # Enable -fPIC for static lib
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
FetchContent_Declare(
    jsoncpp
    GIT_REPOSITORY https://github.com/open-source-parsers/jsoncpp.git
    GIT_TAG 1.9.5
)
FetchContent_MakeAvailable(jsoncpp)

# ---------------------------------------------------------------------------
# GPU support  (disabled by default – no OpenCL dependency for standard builds)
# Enable with:  cmake -DENABLE_GPU=ON ..
# macOS:  OpenCL ships with the system framework, no extra install needed.
# Linux:  sudo apt install ocl-icd-opencl-dev opencl-headers
# Windows: install GPU vendor drivers (NVIDIA CUDA Toolkit / AMD ROCm / Intel OpenCL SDK)
# ---------------------------------------------------------------------------
option(ENABLE_GPU "Build with OpenCL GPU kernel execution support" OFF)
if(ENABLE_GPU)
    if(APPLE)
        # macOS ships OpenCL as a system framework (-framework OpenCL).
        # The Khronos ICD loader is for Linux/Windows; it does NOT discover
        # Apple's platform and returns 0 platforms on macOS (including Apple Silicon).
        find_package(OpenCL REQUIRED)
        message(STATUS "[Ramanujan] OpenCL GPU support ENABLED via system framework  (macOS, version ${OpenCL_VERSION_STRING})")
    else()
        # Linux / Windows: fetch Khronos headers and ICD loader from source,
        # the same way jsoncpp is fetched above.
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
    message(STATUS "[Ramanujan] OpenCL GPU support DISABLED  (pass -DENABLE_GPU=ON to enable)")
endif()
IF(ANDROID)
    # For Android builds, use NDK's JNI headers
    include_directories(${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include)
ELSE()
    # For non-Android builds, use JDK_HOME
    include_directories($ENV{JDK_HOME}/include)
    IF(UNIX AND NOT APPLE)
        include_directories($ENV{JDK_HOME}/include/linux)
    ELSEIF(APPLE)
        include_directories($ENV{JDK_HOME}/include/darwin)
    ENDIF()
ENDIF()

add_definitions(-D_GNU_SOURCE)

# Define the source files
set(SOURCE_FILES
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
        ruleEngineObject/Test.cpp
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

# Normal build target
add_executable(native ${SOURCE_FILES})
#add_library(native SHARED ${SOURCE_FILES})
target_link_libraries(native PRIVATE jsoncpp_static)

# Debug build target
add_library(native_debug SHARED ${SOURCE_FILES})
target_link_libraries(native_debug PRIVATE jsoncpp_static)
set_target_properties(native_debug PROPERTIES COMPILE_FLAGS "-DDEBUG_BUILD")
set_target_properties(native_debug PROPERTIES OUTPUT_NAME "native_debug")

# Link OpenCL into both targets when GPU support is enabled
if(ENABLE_GPU)
    target_link_libraries(native       PRIVATE OpenCL::OpenCL)
    target_link_libraries(native_debug PRIVATE OpenCL::OpenCL)
endif()

#find_package(Boost REQUIRED)
#if(Boost_FOUND)
#    include_directories(${Boost_INCLUDE_DIRS})
#    target_link_libraries(native ${Boost_LIBRARIES})
#    target_link_libraries(native_debug ${Boost_LIBRARIES})
#endif()