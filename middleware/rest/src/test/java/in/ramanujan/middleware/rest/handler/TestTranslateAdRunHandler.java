package in.ramanujan.middleware.rest.handler;

import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.pojo.RuleEngineInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void testResolveEntryCodeWithCustomEntryPoint() throws Exception {
        CodeRunRequest req = new CodeRunRequest();
        req.setEntryPoint("app.py");
        Map<String, String> files = new HashMap<>();
        files.put("app.py", "print('hello app')");
        files.put("util.py", "print('util')");
        req.setFiles(files);

        String code = TranslateAndRunHandler.resolveEntryCode(req, null);
        assertEquals("print('hello app')", code);
    }

    @Test
    public void testResolveEntryCodeFallbackAppPy() throws Exception {
        CodeRunRequest req = new CodeRunRequest();
        Map<String, String> files = new HashMap<>();
        files.put("app.py", "print('hello from app.py')");
        files.put("helper.py", "print('helper')");
        req.setFiles(files);

        String code = TranslateAndRunHandler.resolveEntryCode(req, null);
        assertEquals("print('hello from app.py')", code);
    }

    @Test
    public void testResolveEntryCodeFallbackRunPy() throws Exception {
        CodeRunRequest req = new CodeRunRequest();
        Map<String, String> files = new HashMap<>();
        files.put("run.py", "print('hello from run.py')");
        files.put("helper.py", "print('helper')");
        req.setFiles(files);

        String code = TranslateAndRunHandler.resolveEntryCode(req, null);
        assertEquals("print('hello from run.py')", code);
    }

    @Test
    public void testResolveEntryCodeSingleFileFallback() throws Exception {
        CodeRunRequest req = new CodeRunRequest();
        Map<String, String> files = new HashMap<>();
        files.put("custom_simulation.py", "val = 42");
        req.setFiles(files);

        String code = TranslateAndRunHandler.resolveEntryCode(req, null);
        assertEquals("val = 42", code);
    }

    @Test
    public void testResolveEntryCodeNotFoundThrows() {
        CodeRunRequest req = new CodeRunRequest();
        req.setEntryPoint("missing.py");
        Map<String, String> files = new HashMap<>();
        files.put("other.py", "val = 42");
        req.setFiles(files);

        assertThrows(CompilationException.class, () -> {
            TranslateAndRunHandler.resolveEntryCode(req, null);
        });
    }
}
