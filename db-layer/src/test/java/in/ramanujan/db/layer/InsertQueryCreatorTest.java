package in.ramanujan.db.layer;

import in.ramanujan.db.layer.queryCreator.CustomQuery;
import in.ramanujan.db.layer.queryCreator.InsertQueryCreator;
import in.ramanujan.db.layer.schema.AsyncTaskMiddleware;
import org.junit.Test;

public class InsertQueryCreatorTest {

    @Test
    public void testMiddlewareAsyncTaskInsertQuery() throws Exception {
        AsyncTaskMiddleware asyncTaskMiddleware = new AsyncTaskMiddleware();
        asyncTaskMiddleware.setTaskId("taskId");
        asyncTaskMiddleware.setTaskStatus("taskStatus");
        asyncTaskMiddleware.setResult("result");
        CustomQuery customQuery = new InsertQueryCreator().query(asyncTaskMiddleware, null);
    }
}
