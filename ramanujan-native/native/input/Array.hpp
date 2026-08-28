#ifndef ARRAY_H
#define ARRAY_H

#include <string>
#include <list>
#include <unordered_map>
#include <vector>
#include <fstream>
#include <iostream>
#include "RuleEngineInputUnit.hpp"
#include "rule_engine_input.pb.h"



class Array : public RuleEngineInputUnit {
    public:
        std::string dataType, name, frameCount;
        std::vector<int> dimension;
        int dimensionSize = 0;
        std::unordered_map<std::string, double> values;
        std::string binaryFile;  // path to binary float32 file (optional)

        Array() = default;

        Array(const ramanujan::Array* p) {
            this->id = p->id();
            this->dataType = p->data_type();
            this->name = p->name();
            this->frameCount = p->frame_count();
            for (int d : p->dimension()) {
                this->dimension.push_back(d);
                dimensionSize++;
            }

            if (!p->binary_file().empty()) {
                this->binaryFile = p->binary_file();
                // Don't load into values map — ArrayValue will load directly from file
            } else {
                for (const auto& kv : p->values()) {
                    this->values[kv.first] = kv.second;
                }
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
