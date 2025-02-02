//
// Created by Pranav on 28/09/24.
//

#pragma once

#ifndef NATIVE_DEBUGPOINT_H
#define NATIVE_DEBUGPOINT_H

#include <list>
#include "unordered_map"
#include<string>

#ifdef DEBUG_BUILD

#define DEBUG_PRE() \
        std::shared_ptr<DebugPoint> debugPoint = debugger->getDebugPointToBeCommitted();
#define DEBUG_ADD_DOUBLE_PTR_BEFORE(d1) \
        debugPoint->addBeforeVal(*d1);
#define DEBUG_ADD_DOUBLE_BEFORE(d1) \
        debugPoint->addBeforeVal(d1);
#define DEBUG_ADD_DATA_OP_SET_BEFORE(d1) \
        d1->get();
#define DEBUG_ADD_DATA_DOUBLE_SET_AFTER(d1) \
    debugPoint->addAfterVal(d1);

#else

#define DEBUG_PRE()
#define DEBUG_ADD_DOUBLE_PTR_BEFORE(d1)
#define DEBUG_ADD_DOUBLE_BEFORE(d1)
#define DEBUG_ADD_DATA_OP_SET_BEFORE(d1)
#define DEBUG_ADD_DATA_DOUBLE_SET_AFTER(d1)

#endif


class DebugPoint {
public:
    std::list<double> beforeVal;
    std::list<double> afterVal;
    bool condResult;
    std::string commandId;
    int line = 0;

    std::list<double> currentFuncVal;
    std::unordered_map<std::string, std::string> arrayInFuncCall;

public:
    /*arrayCommandRE will turn this off, so we dont capture intermediate var gets for array resolution inside debug info.*/
    bool canAdd = true;

    void addBeforeVal(double val) {
        if(!canAdd) {
            return;
        }
        beforeVal.push_back(val);
    }

    void addAfterVal(double val) {
        afterVal.push_back(val);
    }

    void addCurrentFuncVal(double val) {
        currentFuncVal.push_back(val);
    }

    void setCondResult(bool result) {
        condResult = result;
    }

    void setCommandId(std::string id) {
        commandId = id;
    }

    void addArrayInFuncCall(std::string key, std::string val) {
        arrayInFuncCall[key] = val;
    }

    void setLine(int line) {
        this->line = line;
    }
};

class Debugger {
private:
    std::list<std::shared_ptr<DebugPoint>> debugPoints;

    std::shared_ptr<DebugPoint> debugPointToBeCommitted;

public:

    void startDebugPoint() {
        debugPointToBeCommitted = std::make_shared<DebugPoint>();
    }

    void commitDebugPoint() {
        debugPoints.push_back(debugPointToBeCommitted);
    }

    std::shared_ptr<DebugPoint> getDebugPointToBeCommitted() {
        return debugPointToBeCommitted;
    }

    void stopAdding() {
        debugPointToBeCommitted->canAdd = false;
    }

    void resumeAdding() {
        debugPointToBeCommitted->canAdd = true;
    }

    void clear() {
        debugPoints.clear();
    }

    const std::list<std::shared_ptr<DebugPoint>> &getDebugPoints() const {
        return debugPoints;
    }
};
extern std::shared_ptr<Debugger> debugger;
#endif //NATIVE_DEBUGPOINT_H
