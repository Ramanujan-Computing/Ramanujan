package in.ramanujan.base.pojo;

import lombok.Data;

@Data
public class CheckStatusQueueEventWithMetadata {
    private CheckStatusQueueEvent checkStatusQueueEvent;
    private Object metadata;
}
