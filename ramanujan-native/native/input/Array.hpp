#ifndef ARRAY_H
#define ARRAY_H

#include <string>
#include <list>
#include <unordered_map>
#include "RuleEngineInputUnit.hpp"



class Array : public RuleEngineInputUnit {
    public:
        std::string dataType, name, frameCount;
        std::vector<int> dimension;
        std::unordered_map<std::string, double> values;

        Array(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->dataType = (*value)["dataType"].asString();
            this->name = (*value)["name"].asString();
            this->frameCount = (*value)["frameCount"].asString();
            for (int i = 0; i < (*value)["dimension"].size(); i++) {
                this->dimension.push_back((*value)["dimension"][i].asInt());
            }

            /*element values be of format
             * values:{
             *  "0_0_0": 1.0,
             *  "0_0_1": 2.0,
             * }
             *
             */

            Json::Value values = (*value)["values"];
            for (Json::Value::iterator it = values.begin(); it != values.end(); it++) {
                this->values[it.key().asString()] = it->asDouble();
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
