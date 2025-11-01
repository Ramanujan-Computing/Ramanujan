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
    virtual void copyDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeCopied) = 0;
    
    // New virtual method for direct value setting - eliminates switch statement overhead
    virtual void setValueInDataContainerValueFunctionCommandRE(DataContainerValueFunctionCommandRE& toBeSet) = 0;
    
    // Combined method to save value and copy from source in one call - eliminates extra pointer hop
    virtual void saveValueAndCopyFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValue* source) = 0;
    
    // Combined method to save current value and restore from saved value in one call - eliminates extra pointer hop
    virtual void saveValueAndRestoreFrom(DataContainerValueFunctionCommandRE& savedValue, DataContainerValueFunctionCommandRE& restoreFrom) = 0;
};
#endif //NATIVE_DATACONTAINERVALUE_H
