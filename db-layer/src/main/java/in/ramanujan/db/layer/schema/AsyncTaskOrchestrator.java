package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("asyncTaskOrchestrator")
public class AsyncTaskOrchestrator {
    @ColumnName("uuid")
    @PrimaryKey(keyValue = Keys.UUID, order = "1")
    public String uuid;

    @ColumnName("status")
    public String status;

    @ColumnName("firstCommandId")
    public String firstCommandId;

    @ColumnName("debug")
    public String debug;
}
