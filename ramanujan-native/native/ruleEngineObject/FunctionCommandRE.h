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

    int varCount = 0;
    int arrCount = 0;
private:
    FunctionCall* functionCommandInfo = nullptr;
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

protected:
    FunctionCommandRE(){};

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
    BuiltInFunctionsImpl() {};

    void destroy() override {
        if(methodArgVariableAddr != nullptr) {
            delete[] methodArgVariableAddr;
        }
        if(methodArgArrayAddr != nullptr) {
            delete[] methodArgArrayAddr;
        }
    }

    void setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) override {
        std::list<double*> methodArgVariableAddrList;
        std::list<ArrayValue**> methodArgArrayAddrList;

        for(int i = 0; i < functionInfoRE->argSize; i++) {
            if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
                arrCount++;
                methodArgArrayAddrList.push_back(((ArrayRE*)functionInfoRE->arguments[i])->getValPtr());
            } else {
                varCount++;
                methodArgVariableAddrList.push_back(((DoublePtr*)functionInfoRE->arguments[i])->getValPtrPtr());
            }
        }

        methodArgVariableAddr = new double*[varCount];
        methodArgArrayAddr = new ArrayValue**[arrCount];

        for(int i = 0; i < varCount; i++) {
            methodArgVariableAddr[i] = methodArgVariableAddrList.front();
            methodArgVariableAddrList.pop_front();
        }

        for(int i = 0; i < arrCount; i++) {
            methodArgArrayAddr[i] = methodArgArrayAddrList.front();
            methodArgArrayAddrList.pop_front();
        }
    }
};

class NINF : public BuiltInFunctionsImpl {
public:
    NINF() {};

    void process() override;
};

class PINF : public BuiltInFunctionsImpl {
public:
    PINF() {};

    void process() override;
};

class RAND : public BuiltInFunctionsImpl {
public:
    RAND() {};

    void process() override;
};

class ABS : public BuiltInFunctionsImpl {
public:
    ABS() {};

    void process() override;
};

class SIN : public BuiltInFunctionsImpl {
public:
    SIN() {};

    void process() override;
};

class COS : public BuiltInFunctionsImpl {
public:
    COS() {};

    void process() override;
};

class TAN : public BuiltInFunctionsImpl {
public:
    TAN() {};

    void process() override;
};

class ASIN : public BuiltInFunctionsImpl {
public:
    ASIN() {};

    void process() override;
};

class ACOS : public BuiltInFunctionsImpl {
public:
    ACOS() {};

    void process() override;
};


static FunctionCommandRE* GetFunctionCommandRE(FunctionCall* functionCommand, std::string& id, std::unordered_map<std::string, RuleEngineInputUnits *> *map)
{
    // match id with the built-in methods, if yes, then create object of that type. Else, create FunctionCommandRE
    if(id == "NINF") {
        return new class NINF();
    } else if(id == "PINF") {
        return new class PINF();
    } else if(id == "RAND") {
        return new class RAND();
    } else if(id == "ABS") {
        return new class ABS();
    } else if(id == "SIN") {
        return new class SIN();
    } else if(id == "COS") {
        return new class COS();
    } else if(id == "TAN") {
        return new class TAN();
    } else if(id == "ASIN") {
        return new class ASIN();
    } else if(id == "ACOS") {
        return new class ACOS();
    }

    return new FunctionCommandRE(functionCommand, (FunctionCallRE *) map->at(functionCommand->id));
}

#endif //NATIVE_FUNCTIONCOMMANDRE_H
