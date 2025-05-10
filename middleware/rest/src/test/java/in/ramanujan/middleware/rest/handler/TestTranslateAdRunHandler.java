package in.ramanujan.middleware.rest.handler;

import in.ramanujan.middleware.base.DagElement;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.pojo.RuleEngineInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class TestTranslateAdRunHandler {
    @Test
    public void testDagGraphCreation() {
        TranslateAndRunHandler translateAndRunHandler = new TranslateAndRunHandler();
        TranslateResponse response = new TranslateResponse();
        DagElement firstDagElement = new DagElement(new RuleEngineInput());
        response.setFirstDagElement(firstDagElement);
        response.setDagElementList(new ArrayList<>());
        setGraph(firstDagElement, response.getDagElementList());
        translateAndRunHandler.createDagElementIdGraph(response);
    }

    private void setGraph(DagElement firstDagElement, List<DagElement> dagElementList) {

    }
}
