package in.robinhood.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.robinhood.ramanujan.developer.console.model.pojo.CodeRunAsyncResponse;
import in.robinhood.ramanujan.developer.console.model.pojo.CodeRunRequest;
import in.robinhood.ramanujan.developer.console.model.pojo.DagElementIdGraph;
import in.robinhood.ramanujan.middleware.base.DagElement;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.pojo.ApiResponse;
import in.robinhood.ramanujan.middleware.base.pojo.TranslateResponse;
import in.robinhood.ramanujan.middleware.base.utils.compilation.CompileErrorChecker;
import in.robinhood.ramanujan.middleware.service.RunService;
import in.robinhood.ramanujan.middleware.service.TranslateService;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.Variable;
import in.robinhood.ramanujan.pojo.ruleEngineInputUnitsExt.array.Array;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TranslateAndRunHandler implements Handler<RoutingContext> {
    @Autowired
    private TranslateService translateService;

    @Autowired
    private RunService runService;

    @Autowired
    private CompileErrorChecker compileErrorChecker;

    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            JsonObject jsonObject = routingContext.getBodyAsJson();
            final CodeRunRequest codeRunRequest = jsonObject.mapTo(CodeRunRequest.class);
            final String toBeDebuggedStr = routingContext.queryParams().get("debug");
            final Boolean toBeDebugged = (toBeDebuggedStr != null && "true".equals(toBeDebuggedStr)) ? true : false;
            String code = codeRunRequest.getCode();
            compileErrorChecker.checkCompilationEntryPoint(code);
            runCode(routingContext, codeRunRequest, toBeDebugged);
        } catch (CompilationException compilationException) {
            apiReactionOnCompialtionException(routingContext, compilationException);
        } catch (Exception e) {
            apiReactionOnGeneralException(routingContext, e);
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

    protected void runCode(RoutingContext routingContext, CodeRunRequest codeRunRequest, Boolean toBeDebugged) {
        Map<String, Variable> variableMap = new HashMap<>();
        Map<String, Array> arrayMap = new HashMap<>();
        final String code = codeRunRequest.getCode().replaceAll("\\n","").replaceAll("\\t","");
        translateService.translate(code, codeRunRequest.getCsvInformationList(), variableMap, arrayMap)
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
                   } else {
                       ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                               runCodeHandler.cause());
                       routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                               .end(JsonObject.mapFrom(apiResponse).toString());
                   }
               });
           } else {
               ApiResponse apiResponse = new ApiResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR.toString(),
                       translateHandler.cause());
               routingContext.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                       .end(JsonObject.mapFrom(apiResponse).toString());
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
}
