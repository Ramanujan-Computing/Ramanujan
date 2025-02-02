package in.ramanujan.db.layer;

import in.ramanujan.db.layer.constants.Keys;
import in.ramanujan.db.layer.queryCreator.CustomQuery;
import in.ramanujan.db.layer.queryCreator.WhereClauseQueryCreator;
import in.ramanujan.db.layer.schema.AsyncTaskMiddleware;
import in.ramanujan.db.layer.schema.AvailableHost;
import in.ramanujan.db.layer.schema.DagElement;
import in.ramanujan.db.layer.schema.VariableMappingDagElement;
import org.junit.Assert;
import org.junit.Test;

public class WhereClauseQueryCreatorTest {
    @Test
    public void testMiddlewareAsyncTaskInsertQuery() throws Exception {
        AsyncTaskMiddleware asyncTaskMiddleware = new AsyncTaskMiddleware();
        asyncTaskMiddleware.setTaskId("taskId");
        CustomQuery customQuery = new WhereClauseQueryCreator().query(asyncTaskMiddleware, "taskId", WhereClauseQueryCreator.WhereTypeQuery.SELECT);
        Assert.assertTrue("taskId".equalsIgnoreCase((String) customQuery.getObjects().get(0)));
        Assert.assertTrue("SELECT * FROM asyncTaskMiddleware WHERE taskId=?".equalsIgnoreCase(customQuery.getSql()));
    }

    @Test
    public void testMiddlewareAsyncTaskDeleteQuery() throws Exception {
        AsyncTaskMiddleware asyncTaskMiddleware = new AsyncTaskMiddleware();
        asyncTaskMiddleware.setTaskId("taskId");
        CustomQuery customQuery = new WhereClauseQueryCreator().query(asyncTaskMiddleware, "taskId", WhereClauseQueryCreator.WhereTypeQuery.DELETE);
        Assert.assertTrue("taskId".equalsIgnoreCase((String) customQuery.getObjects().get(0)));
        Assert.assertTrue("DELETE FROM asyncTaskMiddleware WHERE taskId=?".equalsIgnoreCase(customQuery.getSql()));
    }

    @Test
    public void testMiddlewareAsyncTaskUpdateQuery() throws Exception {
        AsyncTaskMiddleware asyncTaskMiddleware = new AsyncTaskMiddleware();
        asyncTaskMiddleware.setTaskId("taskId");
        asyncTaskMiddleware.setTaskStatus("taskStatus");
        CustomQuery customQuery = new WhereClauseQueryCreator().query(asyncTaskMiddleware, "taskId", WhereClauseQueryCreator.WhereTypeQuery.UPDATE);
        Assert.assertTrue("taskStatus".equalsIgnoreCase((String) customQuery.getObjects().get(0)));
        Assert.assertTrue("taskId".equalsIgnoreCase((String) customQuery.getObjects().get(1)));
        Assert.assertTrue("UPDATE asyncTaskMiddleware SET taskStatus=? WHERE taskId=?".equalsIgnoreCase(customQuery.getSql()));
    }

    @Test
    public void testSelectForObjectWhoseIndexContainMultiplePrimaryKeyAnnotation() throws Exception {
        VariableMappingDagElement variableMappingDagElement = new VariableMappingDagElement();
        variableMappingDagElement.setDagElementId("dagElementId");
        CustomQuery customQuery = new WhereClauseQueryCreator().query(variableMappingDagElement, Keys.DAG_ELEMENT_ID, WhereClauseQueryCreator.WhereTypeQuery.SELECT);
        String sql = "SELECT * FROM variableMappingDagElementId WHERE dagElementId=?";
        Assert.assertTrue(sql.equalsIgnoreCase(customQuery.getSql()));
        Assert.assertTrue(customQuery.getObjects().get(0).equals("dagElementId"));


        DagElement dagElement = new DagElement();
        dagElement.setDagElementId("dagElementId");
        dagElement.setPart(1);
        customQuery = new WhereClauseQueryCreator().query(dagElement, Keys.DAG_ELEMENT_ID_PART, WhereClauseQueryCreator.WhereTypeQuery.SELECT);
        sql = "SELECT * FROM dagElement where dagElementId=? and part=?";
        Assert.assertTrue(sql.equalsIgnoreCase(customQuery.getSql()));
    }

    @Test
    public void testSelectForAvailableHostWhichContainCertainOperation() throws Exception {
        AvailableHost availableHost = new AvailableHost();
        availableHost.setLastUpdate(0L);
        availableHost.setStatus(AvailableHost.Status.OPEN.getValue());
        CustomQuery customQuery = new WhereClauseQueryCreator().query(availableHost, Keys.STATUS_LAST_UPDATED, WhereClauseQueryCreator.WhereTypeQuery.SELECT);
        String sql = "SELECT * FROM availableHost where status=? and lastUpdate>=?";
        Assert.assertTrue(sql.equalsIgnoreCase(customQuery.getSql()));
        Assert.assertTrue(customQuery.getObjects().get(0).equals(AvailableHost.Status.OPEN.getValue()));
        Assert.assertTrue(customQuery.getObjects().get(1).equals(0L));
    }
}
