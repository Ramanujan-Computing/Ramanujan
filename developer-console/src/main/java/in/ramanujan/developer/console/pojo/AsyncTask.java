package in.ramanujan.developer.console.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncTask {
    private String taskId;
    private TaskStatus taskStatus;
    private Object result;

    public static enum TaskStatus {
        PENDING,
        FAILED,
        SUCCESS;
    }
}
