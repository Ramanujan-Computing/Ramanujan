//
// Created by pranav on 25/3/24.
//

#ifndef NATIVE_DATACONTAINERRE_H
#define NATIVE_DATACONTAINERRE_H

#include "../RuleEngineInputUnits.hpp"
#include "AbstractDataContainer.h"

/**
 * This class is child of AbstractDataContainer, and defines how the indexes of array.
 */
class DataContainerRE: public RuleEngineInputUnits, public AbstractDataContainer, public DataOperation {
    public:
        std::string name;
        std::string dataType;

};
#endif //NATIVE_DATACONTAINERRE_H
