#ifndef PROCESSOR_H
#define PROCESSOR_H

#include<unordered_map>
#include<list>
#include<string>

#include "ProcessingResult.hpp"
#include "../input/RuleEngineInput.hpp"
#include "../ruleEngineObject/RuleEngineInputUnits.hpp"




class Processor {
    public:
        std::unordered_map<std::string, ProcessingResult>* process(RuleEngineInput ruleEngineInput,
        std::string firstCommandId);
        Processor();
        ~Processor();
    std::unordered_map<std::string, double>* varChangeMap();
    std::unordered_map<std::string, std::unordered_map<std::string, double>*>* arrChangeMap();
    
    private:
        std::unordered_map<std::string, RuleEngineInputUnits*> * createMap(RuleEngineInput ruleEngineInput);

        void populateFieldsInRuleEngineUnitObjects(std::unordered_map<std::string, RuleEngineInputUnit*>
        mapBetweenIdAndRuleInput, RuleEngineInput ruleEngineInputUnit);

    void fixOperator(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Operation *> operations);

    void fixConditions(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap, std::vector<Condition *> conditions);

    std::unordered_map<double*, double> dataFieldOriginalData;

    std::list<RuleEngineInputUnits*> arrayREs;
    std::list<RuleEngineInputUnits*> variableREs;



    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Command*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<While*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<FunctionCall*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Array*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Constant*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Condition*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Operation*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<If*>* list1);

    void storeInIdMap(std::unordered_map<std::string, RuleEngineInputUnits*> *pMap, std::vector<Variable*>* list1);

    void fixGraph(std::unordered_map<std::string, RuleEngineInputUnits *> *pMap);
};



#endif