#ifndef NATIVE_REDEFINEARRAYCOMMANDRE_H
#define NATIVE_REDEFINEARRAYCOMMANDRE_H

#include "RuleEngineInputUnits.hpp"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include <vector>
#include <string>

class RedefineArrayCommandRE : public RuleEngineInputUnits {
public:
    std::string arrayId;
    std::vector<std::string> newDimensions;
    std::vector<VariableRE*> dimensionVariableREs;
    std::vector<int> staticDimensions; // Stores integer values for static dimensions, 0 if not static
    std::vector<bool> isVariableDimension; // true if dimension is a variable, false if explicit integer

    // Pointer to the ArrayValue* of the target ArrayRE
    ArrayValue **arrayValuePtr = nullptr;

    void destroy() override {
        if (arrayValuePtr) {
            delete *arrayValuePtr; // Assuming ownership of the ArrayValue
            arrayValuePtr = nullptr;
        }
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits*> *map) {
        dimensionVariableREs.clear();
        staticDimensions.clear();
        isVariableDimension.clear();
        // Find the ArrayRE and keep pointer to its ArrayValue*
        auto itArr = map->find(arrayId);
        if (itArr != map->end()) {
            ArrayRE* arrayRE = dynamic_cast<ArrayRE*>(itArr->second);
            if (arrayRE) {
                arrayValuePtr = arrayRE->getValPtr();
            }
        }
        for (const auto& varId : newDimensions) {
            auto it = map->find(varId);
            if (it != map->end()) {
                VariableRE* varRe = dynamic_cast<VariableRE*>(it->second);
                if (varRe) {
                    dimensionVariableREs.push_back(varRe);
                    staticDimensions.push_back(0); // 0 or any sentinel value for non-static
                    isVariableDimension.push_back(true);
                    continue;
                }
            }
            // If not found in map, check if it's an integer
            bool isInt = !varId.empty() && std::all_of(varId.begin(), varId.end(), ::isdigit);
            if (isInt) {
                dimensionVariableREs.push_back(nullptr);
                staticDimensions.push_back(std::stoi(varId));
                isVariableDimension.push_back(false);
            } else {
                // Not a variable and not an integer, treat as invalid or unknown
                dimensionVariableREs.push_back(nullptr);
                staticDimensions.push_back(0);
                isVariableDimension.push_back(false);
            }
        }
    }

    // Optionally, initial values can be added here

    int dimsCount = 0;
    int* dims = nullptr;

    RedefineArrayCommandRE(const std::string& arrayId, const std::vector<std::string>& newDimensions)
        : arrayId(arrayId), newDimensions(newDimensions) {
        dimsCount = newDimensions.size();
        dims = new int[dimsCount];
    }

    ~RedefineArrayCommandRE() {
        if (dims) delete[] dims;
    }

    void process(); // To be implemented: logic to redefine the array in memory
};

#endif // NATIVE_REDEFINEARRAYCOMMANDRE_H
