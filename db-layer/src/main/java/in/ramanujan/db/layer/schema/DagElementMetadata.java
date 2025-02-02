package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("dagElementMetadata")
public class DagElementMetadata {
    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    public String dagElementId;

    @ColumnName("firstCommandId")
    public String firstCommandId;

    @ColumnName("maxPart")
    public Integer maxPart;

    @ColumnName("debugPoints")
    public String debugPoints;
}
