package in.ramanujan.base.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.ramanujan.base.enums.KafkaEventType;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEvent {
    private KafkaEventType kafkaEventType;
}
