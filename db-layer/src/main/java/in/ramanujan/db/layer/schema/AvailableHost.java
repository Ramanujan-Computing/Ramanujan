package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.Operation;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.constants.OperationName;
import lombok.Data;

@Data
@Table("availableHost")
public class AvailableHost {
    @PrimaryKey(keyValue = Keys.HOST_ID, order = "1")
    @ColumnName("hostId")
    public String hostId;

    @PrimaryKey(keyValue = Keys.STATUS_LAST_UPDATED, order="1")
    @ColumnName("status")
    public String status;

    @PrimaryKey(keyValue = Keys.STATUS_LAST_UPDATED, order="2")
    @Operation(OperationName.GREATER_THAN_EQUAL_TO)
    @ColumnName("lastUpdate")
    public Long lastUpdate;

    public enum Status {
        ENGAGED("ENGAGED"),
        OPEN("OPEN");

        private String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
