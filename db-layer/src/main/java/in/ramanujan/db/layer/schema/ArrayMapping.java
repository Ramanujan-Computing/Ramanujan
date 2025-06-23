package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("arrayMapping")
public class ArrayMapping {
    @ColumnName("arrayId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "2")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID_INDEX, order = "2")
    @PrimaryKey(keyValue = Keys.ARRAY_ID, order = "1")
    public String arrayId;

    @ColumnName("arrayName")
    public String arrayName;

    @ColumnName("asyncId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID, order = "1")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID, order = "1")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID_INDEX, order = "1")
    public String asyncId;

    @ColumnName(value = "object", duplicateSeparator = true)
    public String object;

    @ColumnName("indexStr")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_ARRAY_ID_INDEX, order = "3")
    public String indexStr;
}
