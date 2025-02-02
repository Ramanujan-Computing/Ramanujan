package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("variableMapping")
public class VariableMapping {
    @ColumnName("asyncId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID, order = "1")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_VARIABLE_ID, order = "1")
    public String asyncId;

    @ColumnName("variableId")
    @PrimaryKey(keyValue = Keys.ASYNC_ID_VARIABLE_ID, order = "2")
    public String variableId;

    @ColumnName(value = "variableName",duplicateSeparator = true)
    public String variableName;

    @ColumnName("object")
    public String object;
}
