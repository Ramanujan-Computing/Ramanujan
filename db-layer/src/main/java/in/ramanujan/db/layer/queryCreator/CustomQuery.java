package in.ramanujan.db.layer.queryCreator;

import io.vertx.sqlclient.Tuple;
import lombok.Data;

import java.util.List;

@Data
public class CustomQuery {
    private String sql;
    private List<Object> objects;
    /**
     * For the cases of batch queries.
     */
    private List<List<Object>> tupleList;
}
