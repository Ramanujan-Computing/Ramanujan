package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("arrayIdNameMap")
public class ArrayIdNameMap {
    @ColumnName("asyncId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID, order = "1")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "1")
    public String asyncId;

    @ColumnName("arrayId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "2")
    public String arrayId;

    @ColumnName("name")
    public String name;
}
