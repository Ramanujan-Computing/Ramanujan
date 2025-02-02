package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("dagElementCheckpoint")
public class DagElementCheckpoint {
    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID_PART, order = "1")
    public String dagElementId;

    @ColumnName("part")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID_PART, order = "2")
    public Integer part;

    @ColumnName("commandStack")
    public String commandStack;
}
