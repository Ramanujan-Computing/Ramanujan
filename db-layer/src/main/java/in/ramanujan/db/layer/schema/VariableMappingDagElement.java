package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("variableMappingDagElementId")
public class VariableMappingDagElement {
    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    @PrimaryKey(keyValue = Keys.DE_ID_VARIABLE_ID, order = "1")
    public String dagElementId;

    @ColumnName("variableId")
    @PrimaryKey(keyValue = Keys.DE_ID_VARIABLE_ID, order = "2")
    public String variableId;

    @ColumnName("variableName")
    public String variableName;

    @ColumnName("object")
    public String object;
}
