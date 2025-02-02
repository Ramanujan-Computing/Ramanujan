package in.ramanujan.data.logging;


import in.ramanujan.devices.common.logging.Logger;

public class LoggerFactory implements in.ramanujan.devices.common.logging.LoggerFactory {
    @Override
    public Logger getLogger(Class aClass) {
        return new in.ramanujan.data.logging.Logger(aClass);
    }
}
