//
// Created by pranav on 2/3/24.
//

#ifndef NATIVE_RULEENGINEINPUTUNITS_HPP
#define NATIVE_RULEENGINEINPUTUNITS_HPP

#include "CommandProcessing.h"
#include <string>
#include <unordered_map>

class CommandRE; // Forward declaration

class RuleEngineInputUnits {
protected:
  int codeStrPtr;

public:
  std::string id;
  RuleEngineInputUnits *nextUnit =
      nullptr; // Next unit to execute after this unit
  RuleEngineInputUnits *immediateParent =
      nullptr; // Parent scope unit (FunctionCommandRE, WhileRE, IfRE)

  // Flags for control flow propagation
  bool encounteredReturn = false;
  bool encounteredBreak = false;
  bool encounteredContinue = false;

  virtual void
  setFields(std::unordered_map<std::string, RuleEngineInputUnits *> *map) = 0;
  virtual RuleEngineInputUnits *
  process() = 0; // Returns next unit (nullptr on return statement)
  std::string getId() { return id; }

public:
  RuleEngineInputUnits *
  getFromMap(std::unordered_map<std::string, RuleEngineInputUnits *> *map,
             std::string key) {
    std::unordered_map<std::string, RuleEngineInputUnits *>::iterator itr =
        map->find(key);
    if (itr == map->end()) {
      return nullptr;
    }
    return itr->second;
  }

  ~RuleEngineInputUnits() { destroy(); }

private:
  virtual void destroy() = 0;
};

#endif // NATIVE_RULEENGINEINPUTUNITS_HPP
