package in.ramanujan.db.layer.utils;

import in.ramanujan.db.layer.annotations.Operation;
import in.ramanujan.db.layer.annotations.PrimaryKey;
import in.ramanujan.db.layer.annotations.PrimaryKeys;
import in.ramanujan.db.layer.enums.QueryType;
import in.ramanujan.db.layer.schema.*;
import io.vertx.core.Context;
import io.vertx.core.Future;

import java.lang.reflect.Field;
import java.util.*;


class IntegerWrapper {
    int value = 0;

    public void increment() {
        value++;
    }

    public int getValue() {
        return value;
    }
}

public class InMemQueryExecutor {

    /**Map of table, table-data; The table-data is mapped between primaryKey and data corresponding to it.
     * On one primary key, there can be multiple data rows.*/
    private Map<String, Map<String, List<Object>>> inMemDb = new HashMap<>();

    public InMemQueryExecutor() {
        super();
    }

    public void init(Context context) {
        inMemDb.put(ArrayIdNameMap.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(ArrayMapping.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(ArrayMappingDagElement.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(ArrayUpdationLog.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(AsyncTaskMiddleware.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(AsyncTaskOrchestrator.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(AvailableHost.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(DagElement.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(DagElementCheckpoint.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(DagElementMetadata.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(DagElementOrchestratorAsyncIdMapping.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(DagElementRelationShip.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(HostMapping.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(OrchestratorCallLocker.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(OrchestratorMiddlewareMapping.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(VariableMapping.class.getSimpleName(), new TreeMap<>());
        inMemDb.put(VariableMappingDagElement.class.getSimpleName(), new TreeMap<>());
    }

    void insert(Object object) throws Exception{
        Map<String, List<Object>> tableData = inMemDb.get(object.getClass().getSimpleName());
        //iterate through the fields of the object and get the primary key
        //and insert the object in the tableData
        GetAllPrimaryKeyOrders result = getGetAllPrimaryKeyOrders(object);

        for(String key : result.primaryKeyOrder.keySet()) {
            Map<Integer, Object> primaryKeyMap = result.primaryKeyOrder.get(key);
            IntegerWrapper count = result.keyColumnCount.get(key);
            if(count.getValue() == 0) {
                throw new Exception("Primary key not found or multiple primary keys found");
            }

            StringBuilder primaryKey = new StringBuilder();
            for(int i=1; i <= count.getValue(); i++) {
                if(i > 1) {
                    primaryKey.append("_");
                }
                primaryKey.append(primaryKeyMap.get(i));
            }
            String primaryKeyStr = primaryKey.toString();
            List<Object> data = tableData.get(primaryKeyStr);
            if(data == null) {
                data = new ArrayList<>();
                tableData.put(primaryKeyStr, data);
            }

            data.add(object);
        }
    }

    private static GetAllPrimaryKeyOrders getGetAllPrimaryKeyOrders(Object object) throws IllegalAccessException {
        Map<String, Map<Integer, Object>> primaryKeyOrder = new HashMap<>();
        Map<String, IntegerWrapper> keyColumnCount = new HashMap<>();
        for(Field field : object.getClass().getDeclaredFields()) {
            //get the primary key
            //insert the object in the tableData
            if(field.isAnnotationPresent(PrimaryKey.class)) {
                PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
                setKeyOrderAndColumnCountForGivenPrimaryKey(object, field, primaryKeyOrder, primaryKey, keyColumnCount);
            }
            if(field.isAnnotationPresent(PrimaryKeys.class)) {
                PrimaryKeys primaryKeys = field.getAnnotation(PrimaryKeys.class);
                for(PrimaryKey primaryKey : primaryKeys.value()) {
                    setKeyOrderAndColumnCountForGivenPrimaryKey(object, field, primaryKeyOrder, primaryKey, keyColumnCount);
                }
            }
        }
        GetAllPrimaryKeyOrders result = new GetAllPrimaryKeyOrders(primaryKeyOrder, keyColumnCount);
        return result;
    }

    private static void setKeyOrderAndColumnCountForGivenPrimaryKey(Object object, Field field, Map<String, Map<Integer, Object>> primaryKeyOrder, PrimaryKey primaryKey, Map<String, IntegerWrapper> keyColumnCount) throws IllegalAccessException {
        Map<Integer, Object> primaryKeyMapForKeyType = primaryKeyOrder.get(primaryKey.keyValue());
        if(primaryKeyMapForKeyType == null) {
            primaryKeyMapForKeyType = new HashMap<>();
            primaryKeyOrder.put(primaryKey.keyValue(), primaryKeyMapForKeyType);
        }
        primaryKeyMapForKeyType.put(Integer.parseInt(primaryKey.order()), field.get(object));

        IntegerWrapper count = keyColumnCount.get(primaryKey.keyValue());
        if(count == null) {
            count = new IntegerWrapper();
            keyColumnCount.put(primaryKey.keyValue(), count);
        }
        count.increment();
    }

    private static class GetAllPrimaryKeyOrders {
        public final Map<String, Map<Integer, Object>> primaryKeyOrder;
        public final Map<String, IntegerWrapper> keyColumnCount;

        public GetAllPrimaryKeyOrders(Map<String, Map<Integer, Object>> primaryKeyOrder, Map<String, IntegerWrapper> keyColumnCount) {
            this.primaryKeyOrder = primaryKeyOrder;
            this.keyColumnCount = keyColumnCount;
        }
    }

    /**Like insert but, here on the primary key, we would have only one key, if not, raise exception*/
    void update(Object object, String indexType) throws Exception {
        StringBuilder indexBuilder = getIndexBuilder(object, indexType);
        String index = indexBuilder.toString();
        Map<String, List<Object>> tableData = inMemDb.get(object.getClass().getSimpleName());
        List<Object> data = tableData.get(index);
        if(data == null) {
            return;
        }
        for(Object dataObj : data) {
            for(Field field : object.getClass().getDeclaredFields()) {
                if(field.get(object) != null) {
                    field.set(dataObj, field.get(object));
                }
            }
        }
    }

    private static StringBuilder getIndexBuilder(Object object, String indexType) throws Exception {
        Map<Integer, Object> indexOrderMap = new HashMap<>();
        Map<Integer, String> operationKeyIndexMap = new HashMap<>();
        for(Field field : object.getClass().getFields()) {
            if(field.isAnnotationPresent(PrimaryKey.class)) {
                PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
                if(primaryKey.keyValue().equals(indexType)) {
                    buildIndex(object, field, indexOrderMap, primaryKey, operationKeyIndexMap);
                }
            }
            if(field.isAnnotationPresent(PrimaryKeys.class)) {
                PrimaryKeys primaryKeys = field.getAnnotation(PrimaryKeys.class);
                for(PrimaryKey primaryKey : primaryKeys.value()) {
                    if(primaryKey.keyValue().equals(indexType)) {
                        buildIndex(object, field, indexOrderMap, primaryKey, operationKeyIndexMap);
                    }
                }
            }
        }
        StringBuilder indexBuilder = new StringBuilder();
        for(int i=1; i <= indexOrderMap.size(); i++) {
            if(i > 1) {
                indexBuilder.append("_");
            }
            Object obj = indexOrderMap.get(i);
            if(obj == null) {
                throw new Exception("Primary key not found");
            }
            //TODO: to make it better way, right now only >= can happen here.
            if(operationKeyIndexMap.containsKey(i)) {
                indexBuilder.append(operationKeyIndexMap.get(i));
            }
            indexBuilder.append(obj);
        }
        return indexBuilder;
    }

    private static void buildIndex(Object object, Field field, Map<Integer, Object> indexOrderMap, PrimaryKey primaryKey, Map<Integer, String> operationKeyIndexMap) throws IllegalAccessException {
        indexOrderMap.put(Integer.parseInt(primaryKey.order()), field.get(object));

        if(field.isAnnotationPresent(Operation.class)) {
            Operation operation = field.getAnnotation(Operation.class);
            operationKeyIndexMap.put(Integer.parseInt(primaryKey.order()), operation.value());
        }
    }

    List<Object> select(Object object, String indexType) throws Exception{
        StringBuilder indexBuilder = getIndexBuilder(object, indexType);
        String index = indexBuilder.toString();
        //TableData is TreeMap.
        TreeMap<String, List<Object>> tableData = (TreeMap<String, List<Object>>) inMemDb.get(object.getClass().getSimpleName());
        //TODO: to make it better way, right now only >= can happen here.
        List<Object> selectResult;
        if(index.contains(">=")) {
            selectResult = tableData.subMap(index.replace(">=", ""),
                    true, index.replace(">=", "A"), false).values().stream().collect(ArrayList::new, List::addAll, List::addAll);
        } else {
            selectResult = tableData.get(index);
        }
        if(selectResult == null)
        {
            return new ArrayList<>();
        }
        List<Object> finalResult = new ArrayList<>();
        for(Object dataObj : selectResult) {
            for(Field field : object.getClass().getFields()) {
                if(field.get(dataObj) != null) {
                    //get field of dataObj and check if it matches with the field.get(dataObj)
                    if(field.get(dataObj).equals(field.get(object))) {
                        finalResult.add(dataObj);
                    }
                }

            }
        }
        return finalResult;
    }

    void delete(Object object, String indexType) throws  Exception {
        StringBuilder indexBuilder = getIndexBuilder(object, indexType);
        String index = indexBuilder.toString();
        Map<String, List<Object>> tableData = inMemDb.get(object.getClass().getSimpleName());
        List<Object> data = tableData.get(index);
        if(data == null) {
            return;
        }
        
        /**Each data point is copied as value for different kind of keys possible on the data, we have to delete all.*/
        List<Object> dataToBeRemoved = new ArrayList<>();
        for(Object dataObj : new ArrayList<>(data)) {
            GetAllPrimaryKeyOrders result = getGetAllPrimaryKeyOrders(dataObj);
            for(String key : result.primaryKeyOrder.keySet()) {
                if(key.equalsIgnoreCase(index)) {
                    dataToBeRemoved.add(dataObj);
                    continue;
                }

                Map<Integer, Object> primaryKeyMap = result.primaryKeyOrder.get(key);
                IntegerWrapper count = result.keyColumnCount.get(key);
                if(count.getValue() == 0) {
                    throw new Exception("Primary key not found or multiple primary keys found");
                }

                StringBuilder primaryKey = new StringBuilder();
                for(int i=1; i <= count.getValue(); i++) {
                    if(i > 1) {
                        primaryKey.append("_");
                    }
                    primaryKey.append(primaryKeyMap.get(i));
                }
                String primaryKeyStr = primaryKey.toString();
                List<Object> dataForPrimaryKey = tableData.get(primaryKeyStr);
                if(dataForPrimaryKey == null) {
                    throw new Exception("Data not found");
                }
                dataForPrimaryKey.remove(dataObj);
            }
        }

        for(Object dataObj : dataToBeRemoved) {
            data.remove(dataObj);
        }
    }

    /**If the object is already present, update it, else insert it*/
    void upsert(Object object, String indexType, List<Object>... batchObjs) throws Exception {
        StringBuilder indexBuilder = getIndexBuilder(object, indexType);
        String index = indexBuilder.toString();
        List<Object> batchObjsList = new ArrayList<>();
        batchObjsList.add(object);
        if(batchObjs != null && batchObjs.length == 1) {
            batchObjsList.addAll(Arrays.asList(batchObjs));
        }
        Map<String, List<Object>> tableData = inMemDb.get(object.getClass().getSimpleName());
        for(Object obj : batchObjsList) {
            List<Object> data = tableData.get(index);
            if(data == null || data.size() == 0) {
                insert(obj);
            } else {
                update(obj, indexType);
            }
        }
    }

    public Future<List<Object>> execute(Object object, String index, QueryType queryType,
                                        List<Object>... batchOpObjectsListArray) throws Exception {
        try {
            switch (queryType) {
                case INSERT:
                    insert(object);
                    break;
                case UPDATE:
                    update(object, index);
                    break;
                case SELECT:
                    return Future.succeededFuture(select(object, index));
                case DELETE:
                    delete(object, index);
                    break;
                case UPSERT:
                    upsert(object, index, batchOpObjectsListArray);
                    break;
                default:
                    throw new Exception("QueryType not supported");
            }
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
        return Future.succeededFuture();
    }
}
