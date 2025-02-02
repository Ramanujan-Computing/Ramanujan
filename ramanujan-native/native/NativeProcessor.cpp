//
// Created by Pranav on 10/05/24.
//

#include "in_ramanujan_rule_engine_NativeProcessor.h"

JNIEXPORT jobject JNICALL Java_in_ramanujan_rule_engine_NativeProcessor_process
        (JNIEnv *env, jobject obj, jstring ruleEngineInputJsonStr, jstring firstCommandIdStr) {
    // Convert jstring to std::string
    const char *cStr = env->GetStringUTFChars(ruleEngineInputJsonStr, 0);
    std::string str(cStr);
    env->ReleaseStringUTFChars(ruleEngineInputJsonStr, cStr);

    const char *cStr1 = env->GetStringUTFChars(firstCommandIdStr, 0);
    std::string str1(cStr1);
    env->ReleaseStringUTFChars(firstCommandIdStr, cStr1);

    // Parse JSON std::string
    Json::Value root;
    Json::CharReaderBuilder builder;
    std::string errors;
    std::istringstream iss(str);
    Json::parseFromStream(builder, iss, &root, &errors);

    RuleEngineInput *ruleEngineInput = new RuleEngineInput(&root);
    Processor *processor = new Processor();
    processor->process(*ruleEngineInput, str1);

    std::unordered_map<std::string, std::unordered_map<std::string, double> *> *arrayMap = processor->arrChangeMap();
    std::unordered_map<std::string, double> *variableMap = processor->varChangeMap();



    /*
     * Make java map<std::string, Object> result in such way:
     * for each variableMap entry, populate in map;
     * then create an entry with key as 'arrayIndex', whose value is a map<std::string, map<std::string, double>> which contains the
     * arrayMap. Ex:
     * {
     * "id1":"val1", "id":"val2",...,"idN":"valN","arrayIndex":{ "id1":{ "index1":"val1", "index2":"val2",...,"indexN":"valN" },...,"idN":{ "index1":"val1", "index2":"val2",...,"indexN":"valN" } }
     * }
     */

// Create a new map
    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID mapConstructor = env->GetMethodID(mapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject map = env->NewObject(mapClass, mapConstructor);

// Populate the map with variableMap
    for (auto const &x: *variableMap) {
        jstring key = env->NewStringUTF(x.first.c_str());
        jdouble value = x.second;
        jobject valueObject = env->NewObject(env->FindClass("java/lang/Double"), env->GetMethodID(env->FindClass("java/lang/Double"), "<init>", "(D)V"), value);
        env->CallObjectMethod(map, putMethod, key, valueObject);
        env->DeleteLocalRef(key);
        env->DeleteLocalRef(valueObject);
    }

// Create a new map for arrayMap
    jobject arrayMapMap = env->NewObject(mapClass, mapConstructor);
    for (auto const &x: *arrayMap) {
        jstring key = env->NewStringUTF(x.first.c_str());
        jobject innerMap = env->NewObject(mapClass, mapConstructor);
        for (auto const &y: *x.second) {
            jstring innerKey = env->NewStringUTF(y.first.c_str());
            jdouble value = y.second;
            jobject valueObject = env->NewObject(env->FindClass("java/lang/Double"), env->GetMethodID(env->FindClass("java/lang/Double"), "<init>", "(D)V"), value);
            env->CallObjectMethod(innerMap, putMethod, innerKey, valueObject);
            env->DeleteLocalRef(innerKey);
            env->DeleteLocalRef(valueObject);
        }
        env->CallObjectMethod(arrayMapMap, putMethod, key, innerMap);
    }

    env->CallObjectMethod(map, putMethod, env->NewStringUTF("arrayIndex"), arrayMapMap);

    // return map via jni

    // Step 1: Get the class reference
    jclass nativeProcessorClass = env->GetObjectClass(obj);

// Step 2: Get the field ID of the Object field
    jfieldID objectFieldID = env->GetFieldID(nativeProcessorClass, "jniObject", "Ljava/util/HashMap;");
    env->SetObjectField(obj, objectFieldID, map);

    /**
     * Set java std::list of object which match with this project DebugPoint
     *
     * public String commandId;
        public int line;
        public boolean condResult;
        public Map<String, String> arrayInFuncCall;
        public Map<String, String> beforeVal;
        public Map<String, String> afterVal;
        public Map<String, String> currentFuncVal;

        the std::list is populated with the values from the processor->debugger->debugPoints
     */
#ifdef DEBUG_BUILD
    //create java std::list of objects
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAddMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    jobject list = env->NewObject(listClass, listConstructor);

    //Iterate though debugger->debugPoints and populate the std::list
    for (auto const &x: debugger->getDebugPoints()) {
        jclass debugPointClass = env->FindClass("in/robinhood/ramanujan/rule/engine/NativeDebugger");
        jmethodID debugPointConstructor = env->GetMethodID(debugPointClass, "<init>", "()V");
        jobject debugPoint = env->NewObject(debugPointClass, debugPointConstructor);

        jfieldID commandIdFieldID = env->GetFieldID(debugPointClass, "commandId", "Ljava/lang/String;");
        jfieldID lineFieldID = env->GetFieldID(debugPointClass, "line", "I");
        jfieldID condResultFieldID = env->GetFieldID(debugPointClass, "condResult", "Z");
        jfieldID arrayInFuncCallFieldID = env->GetFieldID(debugPointClass, "arrayInFuncCall", "Ljava/util/HashMap;");
        jfieldID beforeValFieldID = env->GetFieldID(debugPointClass, "beforeVal", "Ljava/util/ArrayList;");
        jfieldID afterValFieldID = env->GetFieldID(debugPointClass, "afterVal", "Ljava/util/ArrayList;");
        jfieldID currentFuncValFieldID = env->GetFieldID(debugPointClass, "currentFuncVal", "Ljava/util/ArrayList;");

        env->SetObjectField(debugPoint, commandIdFieldID, env->NewStringUTF(x->commandId.c_str()));
        env->SetIntField(debugPoint, lineFieldID, x->line);
        env->SetBooleanField(debugPoint, condResultFieldID, x->condResult);

        //populate arrayInFuncCall
        jobject arrayInFuncCallMap = env->NewObject(mapClass, mapConstructor);
        for (auto const &y: x->arrayInFuncCall) {
            jstring key = env->NewStringUTF(y.first.c_str());
            jstring value = env->NewStringUTF(y.second.c_str());
            env->CallObjectMethod(arrayInFuncCallMap, putMethod, key, value);
            env->DeleteLocalRef(key);
            env->DeleteLocalRef(value);
        }
        env->SetObjectField(debugPoint, arrayInFuncCallFieldID, arrayInFuncCallMap);

        jclass doubleClass = env->FindClass("java/lang/Double");
        jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");

        //populate beforeVal which is std::list of double
        jobject beforeValMap = env->NewObject(listClass, listConstructor);
        for (auto const &y: x->beforeVal) {
            jdouble value = y;
            jobject valueObject = env->NewObject(doubleClass, doubleConstructor, value);
            env->CallObjectMethod(beforeValMap, listAddMethod, valueObject);
            env->DeleteLocalRef(valueObject);
        }
        env->SetObjectField(debugPoint, beforeValFieldID, beforeValMap);


        //populate afterVal
        jobject afterValMap = env->NewObject(listClass, listConstructor);
        for (auto const &y: x->afterVal) {
            jdouble value = y;
            jobject valueObject = env->NewObject(doubleClass, doubleConstructor, value);
            env->CallObjectMethod(afterValMap, listAddMethod, valueObject);
            env->DeleteLocalRef(valueObject);
        }
        env->SetObjectField(debugPoint, afterValFieldID, afterValMap);

        //populate currentFuncVal
        jobject currentFuncValMap = env->NewObject(listClass, listConstructor);
        for (auto const &y: x->currentFuncVal) {
            jdouble value = y;
            jobject valueObject = env->NewObject(doubleClass, doubleConstructor, value);
            env->CallObjectMethod(currentFuncValMap, listAddMethod, valueObject);
            env->DeleteLocalRef(valueObject);
            //env->CallObjectMethod(currentFuncValMap, listAddMethod, value);
        }
        env->SetObjectField(debugPoint, currentFuncValFieldID, currentFuncValMap);

        env->CallBooleanMethod(list, listAddMethod, debugPoint);

    }

    jfieldID objectFieldID2 = env->GetFieldID(nativeProcessorClass, "debugPoints", "Ljava/util/ArrayList;");
    env->SetObjectField(obj, objectFieldID2, list);
#endif

    delete processor;


    return map;
}
