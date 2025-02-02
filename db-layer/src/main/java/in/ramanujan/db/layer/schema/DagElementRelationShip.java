package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("dagElementRelationship")
public class DagElementRelationShip {
    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DE_ID_NEXT_DE_ID, order = "1")
    public String dagElementId;

    @ColumnName("nextDagElementId")
    @PrimaryKey(keyValue = Keys.NEXT_DAG_ELEMENT_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DE_ID_NEXT_DE_ID, order = "2")
    public String nextDagElementId;

    @ColumnName("relation")
    public String relation;

}
