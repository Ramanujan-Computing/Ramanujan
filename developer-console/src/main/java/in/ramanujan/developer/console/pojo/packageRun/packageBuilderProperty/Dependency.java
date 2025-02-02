package in.ramanujan.developer.console.pojo.packageRun.packageBuilderProperty;

import lombok.Data;

@Data
public class Dependency {
    private String groupId;
    private String artifactId;
    private String version;
}
