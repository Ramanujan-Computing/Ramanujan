//
// Created by Pranav on 10/05/24.
//

#include <iostream>
#include <chrono>
#include <fstream>
#include <sstream>
#include <vector>
#include <algorithm>
#include <cstdlib>
#include <unordered_map>
#include <string>

#include "Processor.hpp"
#include "dataContainer/ArrayRE.h"
#include "rule_engine_input.pb.h"

class Test {
    const std::vector<std::string> tmpDebugPaths = {
        "/tmp/rule_engine_debug.pb",
        "/tmp/rule_engine_debug.bin",
        "/tmp/rule_engine_debug.json"
    };
    const std::string tmpMetaPath = "/tmp/rule_engine_debug_meta.json";

    Processor *processor = new Processor();

    bool loadFromTmp(ramanujan::RuleEngineInput &proto, std::string &firstCommandOut) {
        std::string foundPath = "";
        for (const auto& path : tmpDebugPaths) {
            std::ifstream infile(path, std::ios::binary);
            if (infile.good() && proto.ParseFromIstream(&infile)) {
                foundPath = path;
                break;
            }
        }

        if (foundPath.empty()) {
            std::cerr << "Could not find or parse debug payload from tmp files." << std::endl;
            return false;
        }

        std::cout << "Loaded ruleEngineInput protobuf from " << foundPath << std::endl;

        // Try reading firstCommandId from meta file if present
        std::ifstream metaFile(tmpMetaPath);
        if (metaFile.good()) {
            std::string line;
            if (std::getline(metaFile, line)) {
                size_t pos = line.find("firstCommandId");
                if (pos != std::string::npos) {
                    size_t startQuote = line.find('"', pos + 14);
                    if (startQuote != std::string::npos) {
                        size_t endQuote = line.find('"', startQuote + 1);
                        if (endQuote != std::string::npos) {
                            firstCommandOut = line.substr(startQuote + 1, endQuote - startQuote - 1);
                        }
                    }
                }
            }
        }

        if (firstCommandOut.empty() && proto.commands_size() > 0) {
            firstCommandOut = proto.commands(0).id();
        }

        return true;
    }

public:
    void process() {
        ramanujan::RuleEngineInput proto;
        std::string firstCommandId;

        if (!loadFromTmp(proto, firstCommandId)) {
            std::cerr << "Failed to load protobuf input in Test.cpp" << std::endl;
            return;
        }

        std::cout << "Executing with firstCommandId: " << firstCommandId << std::endl;

        RuleEngineInput *ruleEngineInput = new RuleEngineInput(&proto);

        auto start = std::chrono::high_resolution_clock::now();
        processor->process(*ruleEngineInput, firstCommandId);
        auto end = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_seconds = end - start;
        std::cout << "Time taken: " << elapsed_seconds.count() << "s\n";

        std::unordered_map<std::string, std::unordered_map<std::string, double>*> * arrayMap = processor->arrChangeMap();
        std::unordered_map<std::string, double> *variableMap = processor->varChangeMap();

        // ── Dump arrays to CSV if RAMANUJAN_DUMP_ARRAYS is set ──
        // Format: "varname1:/path/to/out1.csv,varname2:/path/to/out2.csv"
        const char* dumpSpec = std::getenv("RAMANUJAN_DUMP_ARRAYS");
        if (dumpSpec != nullptr) {
            std::unordered_map<std::string, std::pair<float*, int>> arrMap;
            std::cerr << "[dump] Available arrays:\n";
            for (auto* unit : processor->getArrayREs()) {
                ArrayRE* arrayRE = (ArrayRE*)unit;
                ArrayValue* av = arrayRE->arrayValue.arrayValue;
                std::cerr << "  name='" << arrayRE->name << "' id='" << arrayRE->id
                          << "' size=" << av->totalSize << "\n";
                if (av->totalSize > 0) {
                    arrMap[arrayRE->name] = {av->val, av->totalSize};
                    arrMap[arrayRE->id] = {av->val, av->totalSize};
                }
            }

            std::string spec(dumpSpec);
            std::istringstream specStream(spec);
            std::string pair;
            while (std::getline(specStream, pair, ',')) {
                size_t colonPos = pair.find(':');
                if (colonPos == std::string::npos) continue;
                std::string varName = pair.substr(0, colonPos);
                std::string outPath = pair.substr(colonPos + 1);

                auto it = arrMap.find(varName);
                if (it == arrMap.end()) {
                    std::cerr << "[dump] Array '" << varName << "' not found\n";
                    std::cerr << "[dump] Available arrays:";
                    for (auto const &e : arrMap) std::cerr << " " << e.first;
                    std::cerr << "\n";
                    continue;
                }

                float* vals = it->second.first;
                int size = it->second.second;
                std::ofstream out(outPath);
                for (int i = 0; i < size; i++) {
                    if (i > 0) out << ",";
                    out << vals[i];
                }
                out << "\n";
                out.close();
                std::cerr << "[dump] " << varName << " -> " << outPath
                          << " (" << size << " values)\n";
            }
        }

        // ── Dump variables if RAMANUJAN_DUMP_VARS is set ──
        const char* dumpVarSpec = std::getenv("RAMANUJAN_DUMP_VARS");
        if (dumpVarSpec != nullptr && variableMap != nullptr) {
            std::string spec(dumpVarSpec);
            std::istringstream specStream(spec);
            std::string pair;
            while (std::getline(specStream, pair, ',')) {
                size_t colonPos = pair.find(':');
                if (colonPos == std::string::npos) continue;
                std::string varName = pair.substr(0, colonPos);
                std::string outPath = pair.substr(colonPos + 1);
                auto it = variableMap->find(varName);
                if (it != variableMap->end()) {
                    std::ofstream out(outPath);
                    out << it->second << "\n";
                    out.close();
                    std::cerr << "[dump] var " << varName << " = " << it->second << " -> " << outPath << "\n";
                } else {
                    std::cerr << "[dump] Variable '" << varName << "' not found\n";
                }
            }
        }

        delete processor;
        delete ruleEngineInput;
    }
};

int main() {
    Test *test = new Test();
    test->process();
    delete test;
    return 0;
}