#include "Processor.hpp"
#include <iomanip>
#include <iostream>
#include <list>
#include <string>
#include <unordered_map>

#include "../ruleEngineObject/CommandRE.h"
#include "../ruleEngineObject/ConditionRE.h"
#include "../ruleEngineObject/DataContainerValueFunctionCommandREMemMaintainer.h"
#include "../ruleEngineObject/FunctionCommandRE.h"
#include "../ruleEngineObject/IfRE.h"
#include "../ruleEngineObject/OperationRE.h"
#include "../ruleEngineObject/WhileRE.h"
#include "../ruleEngineObject/dataContainer/ArrayRE.h"
#include "../ruleEngineObject/dataContainer/VariableRE.h"
#include "ProcessingResult.hpp"
// #include <boost/stacktrace.hpp>
#include <DebugPoint.h>

Processor::Processor() {}

Processor::~Processor() {
  // Free the large double[] val allocations inside each ArrayRE.
  // Without this, every JNI kernel call leaks ~1-2 GB of native heap (the
  // weight arrays), causing the JVM to crash after ~20 layers as native memory
  // is exhausted.
  for (RuleEngineInputUnits *u : arrayREs) {
    ArrayRE *ar = static_cast<ArrayRE *>(u);
    ArrayDataContainerValue *adcv =
        static_cast<ArrayDataContainerValue *>(ar->getVal());
//    if (adcv && adcv->arrayValue) {
//      adcv->arrayValue->destroy();
//    }
  }
  arrayREs.clear();
  variableREs.clear();
}

std::unordered_map<std::string, ProcessingResult> *
Processor::process(RuleEngineInput ruleEngineInput,
                   std::string firstCommandId) {

  // Create memory maintainer for efficient function call memory management
  DataContainerValueFunctionCommandREMemMaintainer *memMaintainer =
      new DataContainerValueFunctionCommandREMemMaintainer();

  std::unordered_map<std::string, RuleEngineInputUnits *>
      *mapBetweenIdAndRuleInput = createMap(ruleEngineInput);

  /*
   * First, call chooseRuleEngineUnits on all CommandRE objects to select their
   * internal units
   */
  int cmdIdx = 0;
  for (auto command : *ruleEngineInput.commands) {
    CommandRE *commandRE =
        dynamic_cast<CommandRE *>(mapBetweenIdAndRuleInput->at(command->id));
    commandRE->chooseRuleEngineUnits(mapBetweenIdAndRuleInput);
    cmdIdx++;
  }

  /*
   * Right now, the map does have info of each other, for ex, lets take Command,
   * it does not have refs to the componenets of the Command.
   */
  fixGraph(mapBetweenIdAndRuleInput);

  fixOperator(mapBetweenIdAndRuleInput, *ruleEngineInput.operations);
  fixReturnOperations(mapBetweenIdAndRuleInput,
                      *ruleEngineInput.returnOperations);
  fixConditions(mapBetweenIdAndRuleInput, *ruleEngineInput.conditions);

  for (RuleEngineInputUnits *variable : variableREs) {
    VariableRE *variableRE = (VariableRE *)(variable);
    dataFieldOriginalData.insert(std::make_pair(variableRE->getValPtrPtr(),
                                                *variableRE->getValPtrPtr()));
  }

  for (RuleEngineInputUnits *array : arrayREs) {
    ArrayRE *arrayRE = (ArrayRE *)(array);
    ArrayValue *arrayValue =
        ((ArrayDataContainerValue *)(arrayRE->getVal()))->arrayValue;
    int size = arrayValue->totalSize;
    if (size > 100000) {
      continue;
    }
    arraySnapshotMap[arrayRE->id] =
        std::vector<float>(arrayValue->val, arrayValue->val + size);
  }

  int fcIdx = 0;
  for (auto command : *ruleEngineInput.commands) {
    auto commandRE =
        dynamic_cast<CommandRE *>(mapBetweenIdAndRuleInput->at(command->id));
    if (commandRE->functionCommandRE != nullptr) {
      // Now call setFields for FunctionCommandRE - deferred from
      // CommandRE::setFields This must be done after all CommandRE are
      // initialized
      commandRE->functionCommandRE->setFields(mapBetweenIdAndRuleInput);
      commandRE->functionCommandRE->setMemMaintainer(memMaintainer);
      fcIdx++;
    }
  }

#ifdef DEBUG_BUILD
  debugger->clear();
#endif
  CommandRE *command =
      dynamic_cast<CommandRE *>(mapBetweenIdAndRuleInput->at(firstCommandId));
  auto unit = command->getUnit();
  while (unit != nullptr) {
    unit = unit->process();
  }

  delete memMaintainer;
  return new std::unordered_map<std::string, ProcessingResult>();
}

std::unordered_map<std::string, double> *Processor::varChangeMap() {
  std::unordered_map<std::string, double> *varChangeMap =
      new std::unordered_map<std::string, double>();
  for (RuleEngineInputUnits *variableRE1 : variableREs) {
    VariableRE *variableRE = (VariableRE *)variableRE1;
    double *valPtr = variableRE->getValPtrPtr();
    // double originalVal = dataFieldOriginalData.at(valPtr);
    double newVal = *valPtr;
    //        if (originalVal != newVal) {
    varChangeMap->insert(std::make_pair(variableRE->id, newVal));
    //        }
  }
  return varChangeMap;
}

std::unordered_map<std::string, std::unordered_map<std::string, double> *> *
Processor::arrChangeMap() {
  std::unordered_map<std::string, std::unordered_map<std::string, double> *>
      *arrChangeMap =
          new std::unordered_map<std::string,
                                 std::unordered_map<std::string, double> *>();

  bool anyMarked = false;
  for (RuleEngineInputUnits *u : arrayREs) {
    if (static_cast<ArrayRE *>(u)->markedForReturn) { anyMarked = true; break; }
  }

  for (RuleEngineInputUnits *arrayRE1 : arrayREs) {
    ArrayRE *arrayRE = (ArrayRE *)arrayRE1;
    if (anyMarked && !arrayRE->markedForReturn) continue;
    auto snapIt = arraySnapshotMap.find(arrayRE->id);
    if (snapIt == arraySnapshotMap.end())
      continue; // large array, not tracked
    const std::vector<float> &snapshot = snapIt->second;

    ArrayDataContainerValue *pArrayDataContainerValueValue =
        (ArrayDataContainerValue *)(arrayRE->getVal());
    ArrayValue *arrayValue = pArrayDataContainerValueValue->arrayValue;
    int size = arrayValue->totalSize;
    int compareSize = (int)snapshot.size() < size ? (int)snapshot.size() : size;

    std::unordered_map<std::string, double> *arrChangeMap1 =
        new std::unordered_map<std::string, double>();
    bool changed = false;
    for (int i = 0; i < compareSize; i++) {
      float originalVal = snapshot[i];
      float newVal = arrayValue->val[i];
      if (originalVal != newVal) {
        arrChangeMap1->insert(std::make_pair(arrayValue->to_string(i), newVal));
        changed = true;
      }
    }
    if (changed)
      arrChangeMap->insert(std::make_pair(arrayRE->id, arrChangeMap1));
  }
  return arrChangeMap;
}

void Processor::fixOperator(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Operation *> operations) {
  for (std::vector<Operation *>::iterator itr = operations.begin();
       itr != operations.end(); itr++) {
    OperationRE *operationRE = (OperationRE *)(pMap->at((*itr)->id));
    operationRE->setCachedOperationFunctioning();
  }
}

void Processor::fixConditions(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Condition *> conditions) {
  for (std::vector<Condition *>::iterator itr = conditions.begin();
       itr != conditions.end(); itr++) {
    ConditionRE *conditionRE = (ConditionRE *)(pMap->at((*itr)->id));
    conditionRE->setCachedConditionFunctioning();
  }
}

std::unordered_map<std::string, RuleEngineInputUnits *> *
Processor::createMap(RuleEngineInput ruleEngineInput) {
  std::unordered_map<std::string, RuleEngineInputUnits *> *map =
      new std::unordered_map<std::string, RuleEngineInputUnits *>;

  storeInIdMap(map, ruleEngineInput.methodAgnosticVariables);
  storeInIdMap(map, ruleEngineInput.variables);
  storeInIdMap(map, ruleEngineInput.ifBlocks);
  storeInIdMap(map, ruleEngineInput.operations);
  storeInIdMap(map, ruleEngineInput.conditions);
  storeInIdMap(map, ruleEngineInput.constants);
  storeInIdMap(map, ruleEngineInput.arrays);
  storeInIdMap(map, ruleEngineInput.functionCalls);
  storeInIdMap(map, ruleEngineInput.whileBlocks);
  storeInIdMap(map, ruleEngineInput.commands);
  storeInIdMap(map, ruleEngineInput.redefineArrayCommands);
  storeInIdMap(map, ruleEngineInput.returnOperations);
  return map;
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Command *> *list1) {
  for (std::vector<Command *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    if ((*itr)->id == "command_f52a5304-32d6-41de-a9db-9a0f5c48f97b") {
      std::cout << "Command found" << std::endl;
    }
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<MethodAgnosticVariable *> *list1) {
  for (std::vector<MethodAgnosticVariable *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    MethodAgnosticVariableRE *var =
        (MethodAgnosticVariableRE *)(*itr)->getInternalAnalogy();
    pMap->insert(std::make_pair((*itr)->id, var));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Variable *> *list1) {
  for (std::vector<Variable *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    VariableRE *var = (VariableRE *)(*itr)->getInternalAnalogy();
    variableREs.push_back(var);
    pMap->insert(std::make_pair((*itr)->id, var));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<If *> *list1) {
  for (std::vector<If *>::iterator itr = list1->begin(); itr != list1->end();
       itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Operation *> *list1) {
  for (std::vector<Operation *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Condition *> *list1) {
  for (std::vector<Condition *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Constant *> *list1) {
  for (std::vector<Constant *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<Array *> *list1) {
  for (std::vector<Array *>::iterator itr = list1->begin(); itr != list1->end();
       itr++) {
    ArrayRE *arrayRE = (ArrayRE *)(*itr)->getInternalAnalogy();
    arrayREs.push_back(arrayRE);
    pMap->insert(std::make_pair((*itr)->id, arrayRE));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<FunctionCall *> *list1) {
  for (std::vector<FunctionCall *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<While *> *list1) {
  for (std::vector<While *>::iterator itr = list1->begin(); itr != list1->end();
       itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::fixGraph(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap) {
  // First pass: setFields for all units EXCEPT WhileRE and IfRE
  for (std::unordered_map<std::string, RuleEngineInputUnits *>::iterator itr =
           pMap->begin();
       itr != pMap->end(); itr++) {
    //        try {
    if (itr->first == "command_f52a5304-32d6-41de-a9db-9a0f5c48f97b") {
      std::cout << "Command found" << std::endl;
    }
    // Check if it's a WhileRE or IfRE - defer these
    WhileRE *whileRE = dynamic_cast<WhileRE *>(itr->second);
    if (whileRE != nullptr) {
      deferredWhileREs.push_back(whileRE);
      continue;
    }
    IfRE *ifRE = dynamic_cast<IfRE *>(itr->second);
    if (ifRE != nullptr) {
      deferredIfREs.push_back(ifRE);
      continue;
    }
    itr->second->setFields(pMap);
    //        } catch (exception e) {
    //            std::cerr << "Exception caught, stacktrace: " <<
    //            boost::stacktrace::stacktrace() << '\n';
    //
    //
    //        }
  }

  // Second pass: setFields for WhileRE (needs CommandRE to be initialized
  // first)
  for (RuleEngineInputUnits *unit : deferredWhileREs) {
    unit->setFields(pMap);
  }

  // Third pass: setFields for IfRE (needs CommandRE to be initialized first)
  for (RuleEngineInputUnits *unit : deferredIfREs) {
    unit->setFields(pMap);
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<RedefineArrayCommand *> *list1) {
  for (auto itr = list1->begin(); itr != list1->end(); ++itr) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::storeInIdMap(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<ReturnOperation *> *list1) {
  for (std::vector<ReturnOperation *>::iterator itr = list1->begin();
       itr != list1->end(); itr++) {
    pMap->insert(std::make_pair((*itr)->id, (*itr)->getInternalAnalogy()));
  }
}

void Processor::fixReturnOperations(
    std::unordered_map<std::string, RuleEngineInputUnits *> *pMap,
    std::vector<ReturnOperation *> returnOperations) {
  // ReturnOperations don't need caching like Operations do, as they always use
  // AssignImplBothVar The initialization happens in
  // ReturnOperationRE::setFields
}
