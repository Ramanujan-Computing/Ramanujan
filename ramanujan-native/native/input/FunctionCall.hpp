#ifndef FUNC_CALL_H
#define FUNC_CALL_H

#include <string>
#include <list>
#include <vector>
#include "RuleEngineInputUnit.hpp"



class FunctionCall : public RuleEngineInputUnit {
    public:
        std::string firstCommandId;
        std::vector<std::string> arguments;
        int argumentsSize = 0;
        std::vector<std::string> allVariablesInMethod;
        int allVariablesInMethodSize = 0;

        /**
         * True when this function should be executed as an OpenCL GPU kernel.
         * Functions whose Python names end with "_GPU" are compiled to OpenCL and
         * this flag is set by the translation layer.
         */
        bool isGpu = false;

        /**
         * OpenCL C kernel source code generated from the Python function body.
         * Only populated when isGpu == true.
         */
        std::string openClCode;

        /**
         * Zero-based indices into the arguments vector of the range-kernel-dimension parameters,
         * one per NDRange dimension.  At call time these values are passed as global_work_size[]
         * to clEnqueueNDRangeKernel.  The size of this vector equals work_dim.
         * Only meaningful when isGpu == true.
         */
        std::vector<int> gpuParallelismArgIndices;

        /**
         * Zero-based index of the M parameter (work_dim count) in the argument list.
         * Always equals gpuParallelismArgIndices.size() at call time.
         * Only meaningful when isGpu == true.
         */
        int gpuWorkDimArgIndex = -1;

        FunctionCall(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->immediateParentRuleEngineInputUnitId = (*value)["immediateParentRuleEngineInputUnitId"].asString();
            this->firstCommandId = (*value)["firstCommandId"].asString();
            for (int i = 0; i < (*value)["arguments"].size(); i++) {
                this->arguments.push_back((*value)["arguments"][i].asString());
                argumentsSize++;
            }

            for(int i=0; i<(*value)["allVariablesInMethod"].size(); i++){
                this->allVariablesInMethod.push_back((*value)["allVariablesInMethod"][i].asString());
                allVariablesInMethodSize++;
            }

            // GPU fields (optional – absent in non-GPU functions)
            if (!(*value)["isGpu"].isNull() && (*value)["isGpu"].isBool()) {
                this->isGpu = (*value)["isGpu"].asBool();
            }
            if (!(*value)["openClCode"].isNull() && (*value)["openClCode"].isString()) {
                this->openClCode = (*value)["openClCode"].asString();
            }
            if (!(*value)["gpuParallelismArgIndices"].isNull() && (*value)["gpuParallelismArgIndices"].isArray()) {
                for (int idx = 0; idx < (int)(*value)["gpuParallelismArgIndices"].size(); idx++) {
                    this->gpuParallelismArgIndices.push_back((*value)["gpuParallelismArgIndices"][idx].asInt());
                }
            }
            if (!(*value)["gpuWorkDimArgIndex"].isNull() && (*value)["gpuWorkDimArgIndex"].isInt()) {
                this->gpuWorkDimArgIndex = (*value)["gpuWorkDimArgIndex"].asInt();
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif