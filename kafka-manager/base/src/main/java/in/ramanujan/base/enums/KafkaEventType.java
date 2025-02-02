package in.ramanujan.base.enums;

import in.ramanujan.base.pojo.CheckStatusQueueEvent;
import in.ramanujan.base.pojo.KafkaEvent;

public enum KafkaEventType {
    check_status_dag_element(CheckStatusQueueEvent.class);

    private Class<KafkaEvent> tClass;

    KafkaEventType(Class tClass) {
        this.tClass = tClass;
    }

    public Class gettClass() {
        return tClass;
    }

    public void settClass(Class tClass) {
        this.tClass = tClass;
    }
}
