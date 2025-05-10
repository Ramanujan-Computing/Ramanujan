package in.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.middleware.service.RunService;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.utils.compilation.PackageCompileErrorChecker;
import io.vertx.ext.web.RoutingContext;

public class PackageRunner extends TranslateAndRunHandler  {

    public RunService runService;
    public PackageCompileErrorChecker packageCompileErrorChecker;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            PackageRunInput packageRunnerInput = objectMapper.readValue(routingContext.getBodyAsJson().toString(),
                    PackageRunInput.class);
            final String toBeDebuggedStr = routingContext.queryParams().get("debug");
            final Boolean toBeDebugged = (toBeDebuggedStr != null && "true".equals(toBeDebuggedStr)) ? true : false;
            packageCompileErrorChecker.checkPackageForCompilation(packageRunnerInput);
            runCode(routingContext, packageRunnerInput, toBeDebugged);
        } catch (CompilationException e) {
            apiReactionOnCompialtionException(routingContext, e);
        } catch (Exception e) {
            apiReactionOnGeneralException(routingContext, e);
        }
    }
}
