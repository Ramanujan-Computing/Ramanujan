#ifndef NATIVE_REDEFINEARRAYCOMMANDREPROCESSING_H
#define NATIVE_REDEFINEARRAYCOMMANDREPROCESSING_H

#include "../CommandTypeProcessingDefinition.h"
#include "../RedefineArrayCommandRE.h"

class RedefineArrayCommandReProcessing : public CommandTypeProcessingDefinition {
private:
    RedefineArrayCommandRE* redefineArrayCommandRE;
public:
    RedefineArrayCommandReProcessing(RedefineArrayCommandRE* redefineArrayCommandRE) {
        this->redefineArrayCommandRE = redefineArrayCommandRE;
    }
    void get() override {
        redefineArrayCommandRE->process();
    }
};

#endif //NATIVE_REDEFINEARRAYCOMMANDREPROCESSING_H
