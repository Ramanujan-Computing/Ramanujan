package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("arrayMappingDagElement")
public class ArrayMappingDagElement {
    @ColumnName("arrayId")
    @PrimaryKey(keyValue = Keys.DE_ID_ARRAY_ID, order = "2")
    @PrimaryKey(keyValue = Keys.DE_ID_ARRAY_ID_INDEX, order = "2")
    public String arrayId;

    @ColumnName("arrayName")
    public String arrayName;

    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DE_ID_ARRAY_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DE_ID_ARRAY_ID_INDEX, order = "1")
    public String dagElementId;

    @ColumnName("object")
    public String object;

    @ColumnName("indexStr")
    @PrimaryKey(keyValue = Keys.DE_ID_ARRAY_ID_INDEX, order = "3")
    public String indexStr;
}
