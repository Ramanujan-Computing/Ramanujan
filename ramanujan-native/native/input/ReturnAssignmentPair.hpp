//
// Created by ramanujan on 26/1/26.
//

#ifndef NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP
#define NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP

#include <string>
#include "rule_engine_input.pb.h"

/**
 * Input representation of a return assignment pair.
 */
class ReturnAssignmentPair {
public:
    std::string targetCommandId;
    std::string sourceCommandId;

    ReturnAssignmentPair(const ramanujan::ReturnAssignmentPair* p) {
        this->targetCommandId = p->target_command_id();
        this->sourceCommandId = p->source_command_id();
    }
};

#endif //NATIVE_RETURNASSIGNMENTPAIR_INPUT_HPP
