//
// Created by pranav on 28/3/24.
//

#include "FunctionCommandRE.h"
#include "dataContainer/ArrayRE.h"
#include "dataContainer/VariableRE.h"
#include "DebugPoint.h"
#include <list>

#include <limits>
#include <random>

FunctionCommandRE::FunctionCommandRE(FunctionCall* functionCommand, FunctionCallRE* functionInfo) {
    this->functionCommandInfo = functionCommand;
    this->functionInfoRE = functionInfo;
}

/*
 * How will we process it?
 * 1. functionInfo has the information about the function. It has the arguments and the first command.
 * 2. it has the allVariablesInMethod.
 * 3. when the function has to be started, we would have to put the values to the arg variables only, and other as 0.0
 * 4. when the function is popped, we would have to transfer the value to calling method variables, and the de-set the args in the current function.
 *    This step is to be done with care, as in the case of recursive function, the value of the variable should not be lost.
 */

void FunctionCommandRE::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    functionInfoRE->setFields(map);

    argSize = functionCommandInfo->argumentsSize;

    firstCommand = functionInfoRE->commmandRe;
    if (firstCommand == nullptr) {
        firstCommand = dynamic_cast<CommandRE *>(getFromMap(map, functionInfoRE->functionCall->firstCommandId));
    }


    /*
     * Demarcate the functionInfoRE->argument in variables and arrays.
     */

    std::list<double*> methodCalledOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCalledArrayPlaceHolderAddrsList;

    std::list<double*> methodCallingOriginalPlaceHolderAddrsList;
    std::list<ArrayValue**> methodCallingArrayPlaceHolderAddrsList;

    for(int i = 0; i < functionInfoRE->argSize; i++) {
        if(dynamic_cast<ArrayRE*>(functionInfoRE->arguments[i]) != nullptr) {
            arrCount++;
            methodCalledArrayPlaceHolderAddrsList.push_back(((ArrayRE*)functionInfoRE->arguments[i])->getValPtr());
            methodCallingArrayPlaceHolderAddrsList.push_back(((ArrayRE*)map->at(functionCommandInfo->arguments[i]))->getValPtr());
            arrayNameMethodMap.insert(std::make_pair(((ArrayRE *) map->at(functionCommandInfo->arguments[i]))->name,
                                                ((ArrayRE *) functionInfoRE->arguments[i])->name));
        } else {
            varCount++;
            methodCalledOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)functionInfoRE->arguments[i])->getValPtrPtr());
            methodCallingOriginalPlaceHolderAddrsList.push_back(((DoublePtr*)map->at(functionCommandInfo->arguments[i]))->getValPtrPtr());
        }
    }

    methodCallingOriginalPlaceHolderAddrs = new double*[varCount];
    methodCallingArrayPlaceHolderAddrs = new ArrayValue**[arrCount];
    methodCalledOriginalPlaceHolderAddrs = new double*[varCount];
    methodCalledArrayPlaceHolderAddrs = new ArrayValue**[arrCount];

    for(int i = 0; i < varCount; i++) {
        methodCalledOriginalPlaceHolderAddrs[i] = methodCalledOriginalPlaceHolderAddrsList.front();
        methodCalledOriginalPlaceHolderAddrsList.pop_front();

        methodCallingOriginalPlaceHolderAddrs[i] = methodCallingOriginalPlaceHolderAddrsList.front();
        methodCallingOriginalPlaceHolderAddrsList.pop_front();
    }

    for(int i = 0; i < arrCount; i++) {
        methodCalledArrayPlaceHolderAddrs[i] = methodCalledArrayPlaceHolderAddrsList.front();
        methodCalledArrayPlaceHolderAddrsList.pop_front();

        methodCallingArrayPlaceHolderAddrs[i] = methodCallingArrayPlaceHolderAddrsList.front();
        methodCallingArrayPlaceHolderAddrsList.pop_front();
    }

    std::list<double*> methodArgVariableAddrList;
    std::list<ArrayValue**> methodArgArrayAddrList;

    for(int i = 0; i < functionInfoRE->functionCall->allVariablesInMethodSize; i++) {
        if(dynamic_cast<ArrayRE*>(functionInfoRE->allVariablesInMethod[i]) != nullptr) {
            totalArrCount++;
            methodArgArrayAddrList.push_back(((ArrayRE*)functionInfoRE->allVariablesInMethod[i])->getValPtr());
        } else {
            totalVarCount++;
            methodArgVariableAddrList.push_back(((DoublePtr*)functionInfoRE->allVariablesInMethod[i])->getValPtrPtr());
        }
    }

    methodArgVariableAddr = new double*[totalVarCount];
    methodArgArrayAddr = new double**[totalArrCount];
    methodArgArrayTotalSize = new int[totalArrCount];

    methodArgVariableCurrentVal = new double[totalVarCount];
    methodArgArrayCurrentVal = new double*[totalArrCount];

    for(int i = 0; i < totalVarCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }
    for(int i = 0; i < totalArrCount; i++) {
        methodArgArrayAddr[i] = &(*methodArgArrayAddrList.front())->val;
        methodArgArrayTotalSize[i] = (*methodArgArrayAddrList.front())->totalSize;
        methodArgArrayAddrList.pop_front();
    }
    variableStackCurrent = new double[varCount];
    arrayStackCurrent = new ArrayValue*[arrCount];
}

void FunctionCommandRE::process() {
#ifdef DEBUG_BUILD
    std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#endif
    for (int i = 0; i < varCount; i++) {
#ifdef DEBUG_BUILD
        debugPoint->addCurrentFuncVal(*methodCallingOriginalPlaceHolderAddrs[i]);
#endif
        variableStackCurrent[i] = *methodCalledOriginalPlaceHolderAddrs[i];
        *methodCalledOriginalPlaceHolderAddrs[i] = (*methodCallingOriginalPlaceHolderAddrs[i]);
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
    }
#ifdef DEBUG_BUILD
    //iterate arrayNameMethodMap and add to debugPoint
    for(auto it = arrayNameMethodMap.begin(); it != arrayNameMethodMap.end(); it++) {
        debugPoint->addArrayInFuncCall(it->first, it->second);
    }
    debugger->commitDebugPoint();
#endif
    for(int i=varCount; i < totalVarCount; i++) {
        methodArgVariableCurrentVal[i] = *methodArgVariableAddr[i];
    }
    for(int i = 0; i < arrCount; i++) {
        arrayStackCurrent[i] = *methodCalledArrayPlaceHolderAddrs[i];
        *methodCalledArrayPlaceHolderAddrs[i] = *methodCallingArrayPlaceHolderAddrs[i];
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];
    }
    for(int i=arrCount;i< totalArrCount;i++) {
        methodArgArrayCurrentVal[i] = *methodArgArrayAddr[i];
        *methodArgArrayAddr[i] = new double[methodArgArrayTotalSize[i]];
    }


    CommandRE* command = firstCommand;
    while(command != nullptr) {
        command = command->get();
    }

    /*
     * We need pop no matter, for ex:
     * func(a,b) {
     *      c = a+ 1
     *      func(c,b)
     *      d = a + 1   <- If not popped, a will have c val.
     * }
     * */

    for(int i=0; i< varCount;i++) {
        variableStackCurrent[i] = *methodCalledOriginalPlaceHolderAddrs[i];
    }
    for(int i=0; i< arrCount;i++) {
        arrayStackCurrent[i] = *methodCalledArrayPlaceHolderAddrs[i];
    }

    for(int i = 0; i < varCount; i++) {
        *methodArgVariableAddr[i] = methodArgVariableCurrentVal[i];
        *methodCallingOriginalPlaceHolderAddrs[i] = variableStackCurrent[i];
    }
    for(int i=varCount; i< totalVarCount; i++) {
        *methodArgVariableAddr[i] = methodArgVariableCurrentVal[i];
    }
    for(int i=0; i< arrCount; i++) {
        *methodArgArrayAddr[i] = methodArgArrayCurrentVal[i];
        *methodCallingArrayPlaceHolderAddrs[i] = arrayStackCurrent[i];
    }
    for(int i=arrCount;i< totalArrCount;i++) {
        delete[] *methodArgArrayAddr[i];
        *methodArgArrayAddr[i] = methodArgArrayCurrentVal[i];
    }


}

void BuiltInFunctionsImpl::setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) {
    std::list<double *> methodArgVariableAddrList;
    std::list<ArrayValue **> methodArgArrayAddrList;

    for (int i = 0; i < functionCommandInfo->argumentsSize; i++) {
        auto arg = map->at(functionCommandInfo->arguments[i]);
        if (dynamic_cast<ArrayRE *>(arg) != nullptr) {
            arrCount++;
            methodArgArrayAddrList.push_back(((ArrayRE *) arg)->getValPtr());
        } else {
            varCount++;
            methodArgVariableAddrList.push_back(((DoublePtr *) arg)->getValPtrPtr());
        }
    }

    methodArgVariableAddr = new double *[varCount];
    methodArgArrayAddr = new ArrayValue **[arrCount];

    for (int i = 0; i < varCount; i++) {
        methodArgVariableAddr[i] = methodArgVariableAddrList.front();
        methodArgVariableAddrList.pop_front();
    }

    for (int i = 0; i < arrCount; i++) {
        methodArgArrayAddr[i] = methodArgArrayAddrList.front();
        methodArgArrayAddrList.pop_front();
    }
}

void NINF::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = -std::numeric_limits<double>::infinity();
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = -std::numeric_limits<double>::infinity();
        }
    }
}

void PINF::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::numeric_limits<double>::infinity();
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = std::numeric_limits<double>::infinity();
        }
    }
}

static std::random_device rd;  // Non-deterministic random seed
static std::mt19937 gen(rd()); // Mersenne Twister engine
static std::uniform_real_distribution<> dis(0.0, 1.0);

void RAND::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = dis(gen);
    }

    if(arrCount == 1)
    {
        ArrayValue** arrayValue = methodArgArrayAddr[0];
        for(int i = 0; i < (*arrayValue)->totalSize; i++) {
            (*arrayValue)->val[i] = dis(gen);
        }
    }
}

// All variable based built-in methods:
void ABS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::abs(*methodArgVariableAddr[0]);
    }
}

void SIN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sin(*methodArgVariableAddr[0]);
    }
}

void COS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::cos(*methodArgVariableAddr[0]);
    }
}

void TAN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::tan(*methodArgVariableAddr[0]);
    }
}

void ASIN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::asin(*methodArgVariableAddr[0]);
    }
}

void ACOS::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::acos(*methodArgVariableAddr[0]);
    }
}

void ATAN::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::atan(*methodArgVariableAddr[0]);
    }
}

void FLOOR::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::floor(*methodArgVariableAddr[0]);
    }
}

void CEIL::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::ceil(*methodArgVariableAddr[0]);
    }
}

void EXP::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::exp(*methodArgVariableAddr[0]);
    }
}

void SQRT::process() {
    if(varCount == 1) {
        *methodArgVariableAddr[0] = std::sqrt(*methodArgVariableAddr[0]);
    }
}

void POW::process() {
    if(varCount == 2) {
        *methodArgVariableAddr[0] = std::pow(*methodArgVariableAddr[0], *methodArgVariableAddr[1]);
    }
}