//
// Created by pranav on 22/3/24.
//

#ifndef NATIVE_FUNCTIONCOMMANDRE_H
#define NATIVE_FUNCTIONCOMMANDRE_H

#include "RuleEngineInputUnits.hpp"
#include "FunctionCall.hpp"
#include "CommandRE.h"
#include "dataContainer/array/ArrayValue.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include "FunctionCallRE.h"
#include<unordered_map>




class FunctionCommandRE : public RuleEngineInputUnits {
private:
    FunctionCall* functionCommandInfo = nullptr;
    FunctionCallRE* functionCommandRE = nullptr;
    FunctionCallRE* functionInfoRE = nullptr;
//    DataContainerRE** arguments;
//    DataContainerRE** functionInfoArgs;



//    double ** argsVariableArr;
//    ArrayValue *** argsArrayArr;
//
//    double ** functionInfoArgsVariableArr;
//    ArrayValue *** functionInfoArgsArrayArr;
//
////    double *** dataContainerVariableValue;
//    ArrayValue ** dataContainerArrayValue;

    int argSize = 0;
    CommandRE* firstCommand;

    int varCount = 0;
    int arrCount = 0;

    int totalVarCount = 0;
    int totalArrCount = 0;

    double** methodCalledOriginalPlaceHolderAddrs = nullptr;
    ArrayValue*** methodCalledArrayPlaceHolderAddrs = nullptr;

    double** methodCallingOriginalPlaceHolderAddrs = nullptr;
    ArrayValue*** methodCallingArrayPlaceHolderAddrs = nullptr;

    double* methodArgVariableCurrentVal = nullptr;
    double** methodArgVariableAddr = nullptr;

    double** methodArgArrayCurrentVal = nullptr;
    double*** methodArgArrayAddr = nullptr;
    int* methodArgArrayTotalSize = nullptr;

    double* variableStackCurrent = nullptr;
    ArrayValue** arrayStackCurrent = nullptr;

    // map of name of array called in method to the array in method.
    /*
     * If its func(arrc) {}
     * and called as func(calledArr);
     *
     * then, entry is <calledArr, arrc>.
     */
    std::unordered_map<std::string, std::string> arrayNameMethodMap;

public:
    FunctionCommandRE(FunctionCall* functionCommmandInfo, FunctionCallRE* functionInfo);
    void destroy() override {
//        for all them, have npe and then delete
//        delete functionCommandInfo;
//        delete functionCommandRE;
//        delete functionInfoRE;
//
//        for(int i=0; i< varCount; i++) {
//            delete argsVariableArr[i];
//            delete functionInfoArgsVariableArr[i];
//        }
//
//        for(int i=0; i< arrCount; i++) {
//            delete argsArrayArr[i];
//            delete functionInfoArgsArrayArr[i];
//            delete dataContainerArrayValue[i];
//        }

        if(functionCommandInfo != nullptr) {
            delete functionCommandInfo;
        }
        if(functionCommandRE != nullptr) {
            delete functionCommandRE;
        }
        if(functionInfoRE != nullptr) {
            delete functionInfoRE;
        }
//        if(argsVariableArr != nullptr) {
//            delete[] argsVariableArr;
//        }
//        if(argsArrayArr != nullptr) {
//            delete[] argsArrayArr;
//        }
//        if(functionInfoArgsVariableArr != nullptr) {
//            delete[] functionInfoArgsVariableArr;
//        }
//        if(functionInfoArgsArrayArr != nullptr) {
//            delete[] functionInfoArgsArrayArr;
//        }
//        if(dataContainerArrayValue != nullptr) {
//            delete[] dataContainerArrayValue;
//        }
    }
    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map);
    void process();
    void processMethod();

};
#endif //NATIVE_FUNCTIONCOMMANDRE_H
