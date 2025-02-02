package in.ramanujan.devices.common;

import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.logging.LoggerFactory;

/*
* Client have to instantiate object of Orchestrator, and call startOrchestrations() to startOrchestration,
* and endOrchestration to stop
* */

public class RamanujanController {
    private Orchestrator orchestrator;

    public RamanujanController(final Credentials credentials, final LoggerFactory loggerFactory) {
        orchestrator = new Orchestrator(credentials, loggerFactory);
        CodeExecutor.init(loggerFactory);
        DebugClient.init(loggerFactory);
    }

    public void startOrchestrations() throws Exception {
        orchestrator.setTimer();
    }
}
