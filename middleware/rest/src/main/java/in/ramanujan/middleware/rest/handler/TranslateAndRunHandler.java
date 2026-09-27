package in.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.model.pojo.CodeRunAsyncResponse;
import in.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.ramanujan.developer.console.model.pojo.DagElementIdGraph;
import in.ramanujan.translation.codeConverter.DagElement;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.middleware.base.pojo.ApiResponse;
import in.ramanujan.translation.codeConverter.pojo.TranslateResponse;
import in.ramanujan.middleware.service.RunService;
import in.ramanujan.middleware.service.TranslateService;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import in.ramanujan.translation.codeConverter.utils.compilation.CompileErrorChecker;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static in.ramanujan.translation.codeConverter.utils.TranslateUtil.isPythonCode;

@Component
public class TranslateAndRunHandler implements Handler<RoutingContext> {
    @Autowired
    private TranslateService translateService;

    @Autowired
    private RunService runService;

    private final static CompileErrorChecker compileErrorChecker = new CompileErrorChecker();

    private static ObjectMapper objectMapper = new ObjectMapper();

    private AtomicInteger currentRequestCount = new AtomicInteger(0);

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            if(currentRequestCount.incrementAndGet() > 5)
            {
                routingContext.response().setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
                        .end(new JsonObject().put("message", "Too many requests, please try again later.").toString());
                currentRequestCount.decrementAndGet();
                return;
            }
            JsonObject jsonObject = routingContext.getBodyAsJson();
            final CodeRunRequest codeRunRequest = jsonObject.mapTo(CodeRunRequest.class);
            final String toBeDebuggedStr = routingContext.queryParams().get("debug");
            final Boolean toBeDebugged = (toBeDebuggedStr != null && "true".equals(toBeDebuggedStr)) ? true : false;
            String code = codeRunRequest.getCode();
            if ((code == null || code.trim().isEmpty()) && codeRunRequest.getAllFiles() != null && !codeRunRequest.getAllFiles().isEmpty()) {
                code = resolveEntryCode(codeRunRequest, routingContext);
                codeRunRequest.setCode(code);
            }
            if (code == null || code.trim().isEmpty()) {
                throw new CompilationException(null, null, "No code or entry point provided to execute");
            }
            if (!isPythonCode(code)) {
                compileErrorChecker.checkCompilationEntryPoint(code);
            }
            runCode(routingContext, codeRunRequest, toBeDebugged, currentRequestCount);
        } catch (CompilationException compilationException) {
            apiReactionOnCompialtionException(routingContext, compilationException);
            currentRequestCount.decrementAndGet();
        } catch (Exception e) {
            apiReactionOnGeneralException(routingContext, e);
            currentRequestCount.decrementAndGet();
        }
    }

    protected void apiReactionOnGeneralException(RoutingContext routingContext, Exception e) {
        ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                e);
        routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(JsonObject.mapFrom(apiResponse).toString());
    }

    protected void apiReactionOnCompialtionException(RoutingContext routingContext, CompilationException compilationException) {
        ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.BAD_REQUEST.toString(),
                compilationException);
        routingContext.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end(JsonObject.mapFrom(apiResponse).toString());
    }

    protected void runCode(RoutingContext routingContext, CodeRunRequest codeRunRequest, Boolean toBeDebugged, AtomicInteger currentRequestCount) {
        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        final String code = isPythonCode(codeRunRequest.getCode()) ? codeRunRequest.getCode() : codeRunRequest.getCode().replaceAll("\\n","").replaceAll("\\t","");
        translateService.translate(code, codeRunRequest.getAllFiles(), codeRunRequest.getCsvInformationList(), variableMap, arrayMap)
                .setHandler(translateHandler -> {
           if(translateHandler.succeeded()) {
               TranslateResponse translateResponse = translateHandler.result();
               runService.runCode(translateResponse, routingContext.vertx(), toBeDebugged).setHandler(runCodeHandler -> {
                   if(runCodeHandler.succeeded()) {
                       CodeRunAsyncResponse codeRunAsyncResponse = new CodeRunAsyncResponse();
                       codeRunAsyncResponse.setAsyncId((String) runCodeHandler.result());
                       codeRunAsyncResponse.setFirstDagElementId(translateResponse.getFirstDagElement().getId());
//                       codeRunAsyncResponse.setDagElementIdGraph(createDagElementIdGraph(translateResponse));
                       ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.OK.toString(), codeRunAsyncResponse);
                       routingContext.response().setStatusCode(HttpResponseStatus.OK.code())
                               .end(JsonObject.mapFrom(apiResponse).toString());
                          currentRequestCount.decrementAndGet();
                   } else {
                       ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                               runCodeHandler.cause());
                       routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                               .end(JsonObject.mapFrom(apiResponse).toString());
                       currentRequestCount.decrementAndGet();
                   }
               });
           } else {
               ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                       translateHandler.cause());
               routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                       .end(JsonObject.mapFrom(apiResponse).toString());
               currentRequestCount.decrementAndGet();
           }
        });
    }

    DagElementIdGraph createDagElementIdGraph(TranslateResponse translateResponse) {
        Map<String, DagElementIdGraph> dagElementMap = new HashMap<>();
        for(DagElement element : translateResponse.getDagElementList()) {
            DagElementIdGraph graphElement = new DagElementIdGraph();
            graphElement.setId(element.getId());
            graphElement.setNextId(new ArrayList<>());
            dagElementMap.put(element.getId(),  graphElement);
        }
        Stack<DagElement> dagElementStack = new Stack<>();
        dagElementStack.push(translateResponse.getFirstDagElement());
        Set<String> pushedElements = new HashSet<>();
        pushedElements.add(translateResponse.getFirstDagElement().getId());
        while(!dagElementStack.empty()) {
            DagElement dagElement = dagElementStack.pop();
            DagElementIdGraph dagElementIdGraph = dagElementMap.get(dagElement.getId());
            for(DagElement nextElement : dagElement.getNextElements()) {
                dagElementIdGraph.getNextId().add(dagElementMap.get(nextElement.getId()));
                String nextElementId = nextElement.getId();
                if(!pushedElements.contains(nextElementId)) {
                    dagElementStack.push(nextElement);
                    pushedElements.add(nextElementId);
                }
            }
        }
        return dagElementMap.get(translateResponse.getFirstDagElement().getId());
    }

    public static String resolveEntryCode(CodeRunRequest codeRunRequest, RoutingContext routingContext) throws CompilationException {
        Map<String, String> allFiles = codeRunRequest.getAllFiles();
        if (allFiles == null || allFiles.isEmpty()) {
            return null;
        }

        String entryPoint = codeRunRequest.getEntryPoint();
        if (entryPoint == null && routingContext != null && routingContext.queryParams() != null) {
            entryPoint = routingContext.queryParams().get("entryPoint");
            if (entryPoint == null) {
                entryPoint = routingContext.queryParams().get("entrypoint");
            }
            if (entryPoint == null) {
                entryPoint = routingContext.queryParams().get("main");
            }
        }

        if (entryPoint != null && !entryPoint.trim().isEmpty()) {
            entryPoint = entryPoint.trim();
            String code = findFileContent(allFiles, entryPoint);
            if (code != null) {
                return code;
            }
            throw new CompilationException(null, null,
                    "Specified entrypoint '" + entryPoint + "' not found in files. Available files: " + allFiles.keySet());
        }

        // Try standard conventions in order
        String[] standardEntrypoints = new String[]{"main.py", "app.py", "run.py", "__main__.py"};
        for (String candidate : standardEntrypoints) {
            String code = findFileContent(allFiles, candidate);
            if (code != null) {
                return code;
            }
        }

        // Single python file fallback
        List<String> pyKeys = new ArrayList<>();
        for (String k : allFiles.keySet()) {
            if (k.endsWith(".py")) {
                pyKeys.add(k);
            }
        }
        if (pyKeys.size() == 1) {
            return allFiles.get(pyKeys.get(0));
        }

        throw new CompilationException(null, null,
                "No entrypoint found in files. Please specify 'code', 'entryPoint' (e.g. 'app.py', 'run.py'), or include main.py/app.py/run.py. Available files: " + allFiles.keySet());
    }

    private static String findFileContent(Map<String, String> files, String target) {
        if (files.containsKey(target)) {
            return files.get(target);
        }
        if (files.containsKey("./" + target)) {
            return files.get("./" + target);
        }
        String cleanTarget = target.startsWith("./") ? target.substring(2) : target;
        if (files.containsKey(cleanTarget)) {
            return files.get(cleanTarget);
        }
        String targetNormalized = cleanTarget.replace('\\', '/');
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String key = entry.getKey().replace('\\', '/');
            while (key.startsWith("./")) {
                key = key.substring(2);
            }
            if (key.equals(targetNormalized) || key.endsWith("/" + targetNormalized)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
