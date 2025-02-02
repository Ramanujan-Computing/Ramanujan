#include "ProcessingResult.hpp"

#include "../ruleEngineObject/dataContainer/array/ArrayValDataContainer.h"
#include "../ruleEngineObject/dataContainer/VariableRE.h"

std::vector<ArrayValueDataContainer*> *ProcessingResult::arrayValDataContainerList = nullptr;
std::vector<VariableRE*> *ProcessingResult::variableREList = nullptr;

void ProcessingResult::addArrayValDataContainer(ArrayValueDataContainer *arrayValueDataContainer) {
    if(arrayValDataContainerList == nullptr) {
        arrayValDataContainerList = new std::vector<ArrayValueDataContainer*>();
    }
    arrayValDataContainerList->push_back(arrayValueDataContainer);
}

void ProcessingResult::addVariableRE(VariableRE *variableRE) {
    if(variableREList == nullptr) {
        variableREList = new std::vector<VariableRE*>();
    }
    variableREList->push_back(variableRE);
}

std::unordered_map<std::string, double> *ProcessingResult::getVarMap() {
    std::unordered_map<std::string, double> *map = new std::unordered_map<std::string, double>();
    for(VariableRE *variableRE : *variableREList) {
        (*map)[variableRE->getId()] = *variableRE->getValPtrPtr();
    }
    return map;
}

std::unordered_map<std::string, std::unordered_map<std::string, double>> *ProcessingResult::getArrayMap() {
    std::unordered_map<std::string, std::unordered_map<std::string, double>> *map = new std::unordered_map<std::string, std::unordered_map<std::string, double>>();
    for(ArrayValueDataContainer *arrayValueDataContainer : *arrayValDataContainerList) {
        (*map)[arrayValueDataContainer->array->getId()][arrayValueDataContainer->getReadableIndex()] = arrayValueDataContainer->get();
    }
    return map;
}