package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;
@Data
@Table("asyncTaskMiddleware")
public class AsyncTaskMiddleware {
    @ColumnName("taskId")
    @PrimaryKey(keyValue = Keys.TASK_ID, order = "1")
    public String taskId;

    @ColumnName("taskStatus")
    public String taskStatus;

    @ColumnName("result")
    public String result;
}
