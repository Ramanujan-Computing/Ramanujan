//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_FUNCTIONCALLRE_H
#define NATIVE_FUNCTIONCALLRE_H

#include "CommandRE.h"
#include "FunctionCall.hpp"
#include "RuleEngineInputUnit.hpp"
#include "dataContainer/DataContainerRE.h"

class FunctionCallRE : public RuleEngineInputUnits {
public:
  FunctionCall *functionCall = nullptr;
  RuleEngineInputUnits **arguments = nullptr;
  RuleEngineInputUnits **allVariablesInMethod = nullptr;
  int argSize = 0;
  RuleEngineInputUnits *commmandRe = nullptr;
  std::string firstCommandId;

  // ---- GPU dispatch fields ----

  /**
   * True when this function should be dispatched as an OpenCL GPU kernel.
   * Mirrors FunctionCall::isGpu after setFields() is called.
   */
  bool isGpu = false;

  /**
   * OpenCL C kernel source code.  Non-empty only when isGpu == true.
   * Mirrors FunctionCall::openClCode.
   */
  std::string openClCode;

  /**
   * Zero-based indices into the arguments array of the range-kernel-dimension
   * parameters, one per NDRange dimension.  At call time these values are
   * global_work_size[] for clEnqueueNDRangeKernel.  Only meaningful when isGpu
   * == true.
   */
  std::vector<int> gpuParallelismArgIndices;

  /**
   * Zero-based index of the M parameter (work_dim count) in the argument list.
   * Its value at call time equals work_dim == gpuParallelismArgIndices.size().
   * Only meaningful when isGpu == true.
   */
  int gpuWorkDimArgIndex = -1;

  bool setFieldDone = false;

  FunctionCallRE(FunctionCall *functionCall1) {
    this->functionCall = functionCall1;
  }

  void destroy() {
    if (functionCall != nullptr)
      delete functionCall;
    if (arguments != nullptr)
      delete[] arguments;
  }

  void setFields(
      std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
    if (setFieldDone) {
      return;
    }
    firstCommandId = functionCall->firstCommandId;
    RuleEngineInputUnits *commandTemp = getFromMap(map, firstCommandId);
    // If it's a CommandRE, get its internal unit
    CommandRE *cmdRE = dynamic_cast<CommandRE *>(commandTemp);
    if (cmdRE != nullptr) {
      commmandRe = cmdRE->getUnit();
    }
    argSize = functionCall->argumentsSize;
    arguments = new RuleEngineInputUnits *[argSize];
    auto itr = functionCall->arguments.begin();
    for (int i = 0; i < functionCall->argumentsSize &&
                    itr != functionCall->arguments.end();
         i++, itr++) {
            arguments[i] = getFromMap(map, (*itr));
    }
    allVariablesInMethod =
        new RuleEngineInputUnits *[functionCall->allVariablesInMethodSize];
    for (int i = 0; i < functionCall->allVariablesInMethodSize; i++) {
            allVariablesInMethod[i] = getFromMap(map, (functionCall->allVariablesInMethod[i]));
    }

    // Populate GPU dispatch fields
    isGpu = functionCall->isGpu;
    openClCode = functionCall->openClCode;
    gpuParallelismArgIndices = functionCall->gpuParallelismArgIndices;
    gpuWorkDimArgIndex = functionCall->gpuWorkDimArgIndex;

    setFieldDone = true;
  }

  RuleEngineInputUnits *process() override {}
};
#endif // NATIVE_FUNCTIONCALLRE_H
