package in.ramanujan.data.db.impl.DagElementVariableDao;

import in.ramanujan.data.db.dao.DagElementVariableDao;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.ArrayMappingDagElement;
import in.ramanujan.db.layer.schema.VariableMappingDagElement;
import in.ramanujan.db.layer.utils.QueryExecutor;
import io.vertx.core.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DagElementVariableDaoSqlDbImpl implements DagElementVariableDao {

    @Autowired
    private QueryExecutor queryExecutor;


    /**
     * For array, variableId -> arrayId:index1_index2_..._indexN
     * */

    @Override
    public Future<Void> insertVariable(String dagElementId, String variableId, Object value) {
        Future<Void> future = Future.future();
        try {

            VariableMappingDagElement variableMappingDagElement = new VariableMappingDagElement();
            variableMappingDagElement.setDagElementId(dagElementId);
            variableMappingDagElement.setVariableId(variableId);
            variableMappingDagElement.setObject(value.toString());
            insertObjectInDB(future, variableMappingDagElement);
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    @Override
    public Future<Void> insertArrayValue(String dagElementId, String arrayId, String index, Object value) {
        Future<Void> future = Future.future();
        try {
            ArrayMappingDagElement arrayMappingDagElement = new ArrayMappingDagElement();
            arrayMappingDagElement.setDagElementId(dagElementId);
            arrayMappingDagElement.setArrayId(arrayId);
            arrayMappingDagElement.setIndexStr(index);
            arrayMappingDagElement.setObject(value.toString());
            insertObjectInDB(future, arrayMappingDagElement);
        } catch (Exception e) {
            future.fail(e);
        }
        return future;
    }

    private void insertObjectInDB(Future<Void> future, Object object) throws Exception {
        queryExecutor.execute(object, null, QueryType.INSERT).setHandler(handler -> {
           if(handler.succeeded()) {
               future.complete();
           } else {
               future.fail(handler.cause());
           }
        });
    }
}
