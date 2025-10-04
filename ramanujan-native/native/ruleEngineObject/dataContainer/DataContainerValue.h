//
// Created by Pranav on 15/06/24.
//

#ifndef NATIVE_DATACONTAINERVALUE_H
#define NATIVE_DATACONTAINERVALUE_H

// Forward declaration
class DataContainerValueFunctionCommandRE;

enum class DataContainerValueType {
    DOUBLE_PTR,
    ARRAY_DATA_CONTAINER_VALUE,
    NONE
};

class DataContainerValue {
public:
    DataContainerValue() = default;
    virtual ~DataContainerValue() = default;
    virtual void copyDataContainerValue(DataContainerValue* toBeCopied) = 0;
    virtual void copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeCopied) = 0;
    virtual DataContainerValue* clone() = 0;
    virtual DataContainerValueType getType() const = 0;
    
    // New virtual method for direct value setting - eliminates switch statement overhead
    virtual void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) = 0;
};
#endif //NATIVE_DATACONTAINERVALUE_H
