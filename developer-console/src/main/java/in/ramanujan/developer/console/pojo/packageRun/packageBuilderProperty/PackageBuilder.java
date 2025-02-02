package in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageBuilder {
    private String artifactId;
    private String groupId;
    private String version;

    private String mainClass;

    private List<Dependency> dependencies;
}
