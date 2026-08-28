#ifndef FUNC_CALL_H
#define FUNC_CALL_H

#include <string>
#include <list>
#include <vector>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class FunctionCall : public RuleEngineInputUnit {
    public:
        std::string firstCommandId;
        std::vector<std::string> arguments;
        int argumentsSize = 0;
        std::vector<std::string> allVariablesInMethod;
        int allVariablesInMethodSize = 0;

        /**
         * True when this function should be executed as an OpenCL GPU kernel.
         */
        bool isGpu = false;

        /**
         * OpenCL C kernel source code generated from the Python function body.
         * Only populated when isGpu == true.
         */
        std::string openClCode;

        /**
         * Zero-based indices into the arguments vector of the range-kernel-dimension parameters.
         * Only meaningful when isGpu == true.
         */
        std::vector<int> gpuParallelismArgIndices;

        /**
         * Zero-based index of the M parameter (work_dim count) in the argument list.
         * Only meaningful when isGpu == true.
         */
        int gpuWorkDimArgIndex = -1;

        FunctionCall(const ramanujan::FunctionCall* p) {
            this->id = p->id();
            this->immediateParentRuleEngineInputUnitId = p->immediate_parent_rule_engine_input_unit_id();
            this->firstCommandId = p->first_command_id();
            for (const auto& a : p->arguments()) {
                this->arguments.push_back(a);
                argumentsSize++;
            }
            for (const auto& v : p->all_variables_in_method()) {
                this->allVariablesInMethod.push_back(v);
                allVariablesInMethodSize++;
            }
            this->isGpu = p->is_gpu();
            this->openClCode = p->open_cl_code();
            for (int idx : p->gpu_parallelism_arg_indices()) {
                this->gpuParallelismArgIndices.push_back(idx);
            }
            this->gpuWorkDimArgIndex = p->gpu_work_dim_arg_index();
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
