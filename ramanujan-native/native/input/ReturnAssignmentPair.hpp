//
// Created by ramanujan on 26/1/26.
//

#ifndef NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP
#define NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP

#include <string>
#include <json/json.h>

/**
 * Input representation of a return assignment pair.
 * Used during deserialization from JSON.
 */
class ReturnAssignmentPair {
public:
    std::string targetCommandId;
    std::string sourceCommandId;

    ReturnAssignmentPair(Json::Value* value) {
        this->targetCommandId = (*value)["targetCommandId"].asString();
        this->sourceCommandId = (*value)["sourceCommandId"].asString();
    }
};

#endif //NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP
