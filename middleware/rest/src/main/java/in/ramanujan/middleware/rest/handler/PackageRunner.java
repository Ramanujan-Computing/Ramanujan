package in.ramanujan.middleware.rest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.ramanujan.developer.console.model.pojo.PackageRunInput;
import in.ramanujan.translation.codeConverter.exception.CompilationException;
import in.ramanujan.translation.codeConverter.utils.compilation.PackageCompileErrorChecker;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PackageRunner extends TranslateAndRunHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    private final static PackageCompileErrorChecker packageCompileErrorChecker = new PackageCompileErrorChecker();
    private AtomicInteger currentRequestCount = new AtomicInteger(0);

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            currentRequestCount.incrementAndGet();
            PackageRunInput packageRunnerInput = objectMapper.readValue(routingContext.getBodyAsJson().toString(),
                    PackageRunInput.class);
            final String toBeDebuggedStr = routingContext.queryParams().get("debug");
            final Boolean toBeDebugged = (toBeDebuggedStr != null && "true".equals(toBeDebuggedStr)) ? true : false;
            packageCompileErrorChecker.checkPackageForCompilation(packageRunnerInput);
            runCode(routingContext, packageRunnerInput, toBeDebugged, currentRequestCount);
        } catch (CompilationException e) {
            currentRequestCount.decrementAndGet();
            apiReactionOnCompialtionException(routingContext, e);
        } catch (Exception e) {
            currentRequestCount.decrementAndGet();
            apiReactionOnGeneralException(routingContext, e);
        }
    }
}
