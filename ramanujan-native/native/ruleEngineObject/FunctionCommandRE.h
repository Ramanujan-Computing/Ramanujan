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
#include <list>


class FunctionCommandRE : public RuleEngineInputUnits {
protected:
    FunctionCallRE* functionCommandRE = nullptr;
    FunctionCallRE* functionInfoRE = nullptr;
    FunctionCall* functionCommandInfo = nullptr;

    int varCount = 0;
    int arrCount = 0;
private:

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

enum BuiltInFunctions {
    NINF, // neg infinte
    PINF, // pos infinite
    RAND, // random
    ABS, // absolute
    SIN, // sin
    COS, // cos
    TAN, // tan
    ASIN, // asin
    ACOS, // acos
};

class BuiltInFunctionsImpl : public FunctionCommandRE {
protected:
    ArrayValue*** methodArgArrayAddr = nullptr;
    double** methodArgVariableAddr = nullptr;
public:

    BuiltInFunctionsImpl(FunctionCall *pCall) : FunctionCommandRE(pCall, nullptr) {}


    void destroy() override {
        if(methodArgVariableAddr != nullptr) {
            delete[] methodArgVariableAddr;
        }
        if(methodArgArrayAddr != nullptr) {
            delete[] methodArgArrayAddr;
        }
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override;
};

class NINF : public BuiltInFunctionsImpl {
public:

    NINF(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}

    void process() override;
};

class PINF : public BuiltInFunctionsImpl {
public:
    PINF(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class RAND : public BuiltInFunctionsImpl {
public:
    RAND(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class ABS : public BuiltInFunctionsImpl {
public:
    ABS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class SIN : public BuiltInFunctionsImpl {
public:
    SIN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class COS : public BuiltInFunctionsImpl {
public:
    COS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class TAN : public BuiltInFunctionsImpl {
public:
    TAN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class ASIN : public BuiltInFunctionsImpl {
public:
    ASIN(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};

class ACOS : public BuiltInFunctionsImpl {
public:
    ACOS(FunctionCall *pCall1) : BuiltInFunctionsImpl(pCall1) {}
    void process() override;
};


static FunctionCommandRE* GetFunctionCommandRE(FunctionCall* functionCommand, std::string& id, std::unordered_map<std::string, RuleEngineInputUnits *> *map)
{
    // match id with the built-in methods, if yes, then create object of that type. Else, create FunctionCommandRE
    if(id == "NINF") {
        return new class NINF(functionCommand);
    } else if(id == "PINF") {
        return new class PINF(functionCommand);
    } else if(id == "RAND") {
        return new class RAND(functionCommand);
    } else if(id == "ABS") {
        return new class ABS(functionCommand);
    } else if(id == "SIN") {
        return new class SIN(functionCommand);
    } else if(id == "COS") {
        return new class COS(functionCommand);
    } else if(id == "TAN") {
        return new class TAN(functionCommand);
    } else if(id == "ASIN") {
        return new class ASIN(functionCommand);
    } else if(id == "ACOS") {
        return new class ACOS(functionCommand);
    }

    return new FunctionCommandRE(functionCommand, (FunctionCallRE *) map->at(functionCommand->id));
}

#endif //NATIVE_FUNCTIONCOMMANDRE_H
