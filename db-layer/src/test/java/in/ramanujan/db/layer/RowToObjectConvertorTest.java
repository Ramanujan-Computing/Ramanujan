package in.ramanujan.db.layer;

import in.ramanujan.db.layer.queryCreator.RowToObjectConvertor;
import in.ramanujan.db.layer.schema.AsyncTaskMiddleware;
import io.vertx.mysqlclient.impl.MySQLRowImpl;
import io.vertx.sqlclient.*;
import io.vertx.sqlclient.impl.RowDesc;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RowToObjectConvertorTest {
    @Test
    public void testForAsyncTaskMiddlewareSelect() throws  Exception {
        List<String> columnNames = new ArrayList<String>() {{
           add("taskId");
           add("taskStatus");
           add("result");
        }};
        RowDesc rowDesc = new RowDesc(columnNames);
        Row row = new MySQLRowImpl(rowDesc);
        row.addValue("taskId");
        row.addValue("taskStatus");
        row.addValue("result");
        AsyncTaskMiddleware asyncTaskMiddleware = (AsyncTaskMiddleware) RowToObjectConvertor.convert(row, new AsyncTaskMiddleware());
        Assert.assertTrue("taskId".equalsIgnoreCase(asyncTaskMiddleware.getTaskId()));
        Assert.assertTrue("taskStatus".equalsIgnoreCase(asyncTaskMiddleware.getTaskStatus()));
        Assert.assertTrue("result".equalsIgnoreCase(asyncTaskMiddleware.getResult()));
    }
}
