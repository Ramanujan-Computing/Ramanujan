package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("dagElementOrchestratorAsyncId")
public class DagElementOrchestratorAsyncIdMapping {
    @ColumnName("dagElementId")
    @PrimaryKey(keyValue = Keys.DAG_ELEMENT_ID, order = "1")
    public String dagElementId;

    @ColumnName("orchestratorAsyncId")
    public String orchestratorAsyncId;
}
