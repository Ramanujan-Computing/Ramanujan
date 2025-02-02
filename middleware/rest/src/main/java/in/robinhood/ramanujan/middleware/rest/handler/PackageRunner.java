package in.robinhood.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.robinhood.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.robinhood.ramanujan.middleware.base.exception.CompilationException;
import in.robinhood.ramanujan.middleware.base.utils.compilation.PackageCompileErrorChecker;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PackageRunner extends TranslateAndRunHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PackageCompileErrorChecker packageCompileErrorChecker;

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
