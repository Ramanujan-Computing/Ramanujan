//
// Created by Pranav on 15/06/24.
//

#ifndef NATIVE_DATACONTAINERVALUE_H
#define NATIVE_DATACONTAINERVALUE_H
class DataContainerValue {
public:
    virtual ~DataContainerValue() = default;
    virtual void copyDataContainerValue(DataContainerValue* toBeCopied) = 0;
    virtual DataContainerValue* clone() = 0;
};
#endif //NATIVE_DATACONTAINERVALUE_H
