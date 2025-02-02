package in.ramanujan.db.layer.schema;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.Table;
import in.ramanujan.db.layer.constants.Keys;
import lombok.Data;

@Data
@Table("hostMapping")
public class HostMapping {
    @ColumnName("uuid")
    @PrimaryKey(keyValue = Keys.UUID, order = "1")
    @PrimaryKey(keyValue = Keys.UUID_HOST_ID, order = "1")
    public String uuid;

    @ColumnName("hostId")
    @PrimaryKey(keyValue = Keys.HOST_ID, order = "1")
    @PrimaryKey(keyValue = Keys.UUID_HOST_ID, order = "2")
    public String hostId;

    @ColumnName("lastPing")
    public Long lastPing;

    @ColumnName("resumeComputation")
    public String resumeComputation;
}
