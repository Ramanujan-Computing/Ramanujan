package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("arrayUpdationLog")
public class ArrayUpdationLog {
    @ColumnName("arrayId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "2")
    public String arrayId;

    @ColumnName("asyncId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "1")
    public String asyncId;

    @ColumnName("dagElementId")
    public String dagElementId;

    @ColumnName("lastUpdate")
    public Long lastUpdate;
}
