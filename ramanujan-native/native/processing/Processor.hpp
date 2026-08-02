#ifndef PROCESSOR_H
#define PROCESSOR_H

#include<unordered_map>
#include<vector>
#include<list>
#include<string>

#include "ProcessingResult.hpp"
#include "../input/RuleEngineInput.hpp"
#include "../ruleEngineObject/RuleEngineInputUnits.hpp"
#include "../ruleEngineObject/DataContainerValueFunctionCommandREMemMaintainer.h"




class Processor {
    public:
        std::unordered_map<std::string, ProcessingResult>* process(RuleEngineInput ruleEngineInput,
        std::string firstCommandId);
        Processor();
        ~Processor();
    std::unordered_map<std::string, double>* varChangeMap();
    std::unordered_map<std::string, std::unordered_map<std::string, double>*>* arrChangeMap();
    // For RETURN()-marked arrays: writes each array's raw float32 contents to a
    // local temp file and returns arrayId -> filePath. Used instead of arrChangeMap()
    // for marked arrays, since they are commonly too large for a point-value map.
    std::unordered_map<std::string, std::string>* binaryReturnArrayFiles();
    std::list<RuleEngineInputUnits*>& getArrayREs() { return arrayREs; }
    std::list<RuleEngineInputUnits*>& getVariableREs() { return variableREs; }
    
    private:
        std::unordered_map<std::string, RuleEngineInputUnits*> * createMap(RuleEngineInput ruleEngineInput);

        void populateFieldsInRuleEngineUnitObjects(std::unordered_map<std::string, RuleEngineInputUnit*>
        mapBetweenIdAndRuleInput, RuleEngineInput ruleEngineInputUnit);

    void fixOperator(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Operation *> operations);

    void fixReturnOperations(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<ReturnOperation *> returnOperations);

    void fixConditions(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap, std::vector<Condition *> conditions);

    std::unordered_map<void*, double> dataFieldOriginalData;
    std::unordered_map<std::string, std::vector<float>> arraySnapshotMap;

    std::list<RuleEngineInputUnits*> arrayREs;
    std::list<RuleEngineInputUnits*> variableREs;
    
    // Deferred setFields - these units need setFields called after all other units
    std::list<RuleEngineInputUnits*> deferredWhileREs;
    std::list<RuleEngineInputUnits*> deferredIfREs;
    std::list<RuleEngineInputUnits*> deferredFunctionCommandREs;



    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Command*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<While*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<FunctionCall*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Array*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Constant*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Condition*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Operation*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<If*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<MethodAgnosticVariable*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Variable*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<RedefineArrayCommand*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<ReturnOperation*>* list1);

    void fixGraph(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap);
};



#endif