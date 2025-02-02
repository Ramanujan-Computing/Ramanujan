#ifndef PROCESSOR_RES_H
#define PROCESSOR_RES_H

#include <list>

class ArrayValueDataContainer;
class VariableRE;

class ProcessingResult {
    static std::vector<ArrayValueDataContainer*> *arrayValDataContainerList;
    static std::vector<VariableRE*> *variableREList;

public:
    static void addArrayValDataContainer(ArrayValueDataContainer* arrayValueDataContainer);
    static void addVariableRE(VariableRE* variableRE);
    static std::unordered_map<std::string, double> *getVarMap();
    static std::unordered_map<std::string, std::unordered_map<std::string, double>> *getArrayMap();
};

#endif