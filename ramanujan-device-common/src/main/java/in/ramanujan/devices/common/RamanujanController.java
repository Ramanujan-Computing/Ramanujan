package in.ramanujan.devices.common;

import in.ramanujan.devices.common.Credentials.Credentials;
import in.ramanujan.devices.common.logging.LoggerFactory;

import static in.ramanujan.devices.common.OrchestratorApiCallHelper.setHost;

/*
* Client have to instantiate object of Orchestrator, and call startOrchestrations() to startOrchestration,
* and endOrchestration to stop
* */

public class RamanujanController {
    private Orchestrator orchestrator;

    public RamanujanController(final String host, final LoggerFactory loggerFactory) {
        setHost(host);
        orchestrator = new Orchestrator(loggerFactory);
        CodeExecutor.init(loggerFactory);
        DebugClient.init(loggerFactory);
    }

    public void startOrchestrations() throws Exception {
        orchestrator.setTimer();
    }
}
