#ifndef ARRAY_H
#define ARRAY_H

#include <string>
#include <list>
#include <unordered_map>
#include <vector>
#include "RuleEngineInputUnit.hpp"



class Array : public RuleEngineInputUnit {
    public:
        std::string dataType, name, frameCount;
        std::vector<int> dimension;
        int dimensionSize = 0;
        std::unordered_map<std::string, double> values;
        int localSequence = -1;
        int globalSequence = -1;

        Array() = default;

        Array(Json::Value* value) {
            this->id = (*value)["id"].asString();
            this->dataType = (*value)["dataType"].asString();
            this->name = (*value)["name"].asString();
            this->frameCount = (*value)["frameCount"].asString();
            for (int i = 0; i < (*value)["dimension"].size(); i++) {
                this->dimension.push_back((*value)["dimension"][i].asInt());
                dimensionSize++;
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
            
            // Handle localSequence and globalSequence fields
            if ((*value).isMember("localSequence") && !(*value)["localSequence"].isNull()) {
                this->localSequence = (*value)["localSequence"].asInt();
            }
            if ((*value).isMember("globalSequence") && !(*value)["globalSequence"].isNull()) {
                this->globalSequence = (*value)["globalSequence"].asInt();
            }
        }

    RuleEngineInputUnits *getInternalAnalogy();
};


#endif
