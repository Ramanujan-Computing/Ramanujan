package in.ramanujan.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckStatusQueueEvent extends KafkaEvent {
    private String dagElementId;
    private String asyncId;
    private Boolean toBeDebugged;
}
