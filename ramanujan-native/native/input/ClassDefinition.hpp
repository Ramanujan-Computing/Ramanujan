#ifndef CLASS_DEFINITION_HPP
#define CLASS_DEFINITION_HPP

#include <string>
#include <vector>
#include <json/json.h>

class ClassDefinition {
public:
    std::string id;
    std::string className;
    std::vector<std::string> scalarFieldNames;
    std::vector<std::string> arrayFieldNames;

    ClassDefinition(Json::Value* value) {
        id = (*value)["id"].asString();
        className = (*value)["className"].asString();
        const Json::Value& sfn = (*value)["scalarFieldNames"];
        for (int i = 0; i < (int)sfn.size(); i++) {
            scalarFieldNames.push_back(sfn[i].asString());
        }
        const Json::Value& afn = (*value)["arrayFieldNames"];
        for (int i = 0; i < (int)afn.size(); i++) {
            arrayFieldNames.push_back(afn[i].asString());
        }
    }
};

#endif
