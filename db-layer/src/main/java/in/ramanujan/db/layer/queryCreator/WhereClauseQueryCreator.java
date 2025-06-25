package in.ramanujan.db.layer.queryCreator;

import in.ramanujan.db.layer.annotations.*;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhereClauseQueryCreator {
    public CustomQuery query(Object obj, String indexName, WhereTypeQuery whereTypeQuery) throws Exception {
        final Table table = obj.getClass().getAnnotation(Table.class);
        List<PrimaryKeyInformation> primaryKeyInformations = new ArrayList<>();
        Map<String, Object> fieldMap = new HashMap<>();
        for(Field field : obj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(ColumnName.class)) {
                Object columnVal = field.get(obj);
                String columnName = ((ColumnName)field.getAnnotation(ColumnName.class)).value();
                if(!addPrimaryKeyInformation(indexName, primaryKeyInformations, field, columnVal, columnName)) {
                    fieldMap.put(columnName, columnVal);
                }
            }
        }
        primaryKeyInformations.sort(((primaryKeyInformation1, primaryKeyInformation2) -> {
            return primaryKeyInformation1.getOrder() - primaryKeyInformation2.getOrder();
        }));
        CustomQuery customQuery = new CustomQuery();
        List<Object> objects = new ArrayList<>();
        int size = primaryKeyInformations.size();
        String whereClause = " WHERE ";
        for(PrimaryKeyInformation primaryKeyInformation : primaryKeyInformations) {
            whereClause += primaryKeyInformation.getColumnName() + primaryKeyInformation.getOperation() + "?";
            if(size > 1) {
                whereClause += " and ";
            }
            size--;
            objects.add(primaryKeyInformation.getColumnValue());
        }
        String sql = "";
        if(whereTypeQuery != WhereTypeQuery.UPDATE) {
            sql = whereTypeQuery.getPrefix() + table.value() + whereClause;
        } else {
            String fieldColumns = "";
            List<Object> setObject = new ArrayList<>();
            Boolean firstColumn = true;
            for(String fieldColumn : fieldMap.keySet()) {
                Object fieldVal = fieldMap.get(fieldColumn);
                if(fieldVal != null) {
                    if(firstColumn) {
                       firstColumn = false;
                    } else {
                        fieldColumns += ",";
                    }
                    fieldColumns += fieldColumn + "=?";
                    setObject.add(fieldVal);
                }
            }
            sql = whereTypeQuery.getPrefix() + table.value() + " SET " + fieldColumns + whereClause;
            objects.addAll(0, setObject);
        }
        customQuery.setSql(sql);
        customQuery.setObjects(objects);

        return customQuery;
    }

    public CustomQuery batchUpdateQuery(Object obj, String indexName, List<Object> batchOpObjects) throws Exception {
        if(batchOpObjects == null || batchOpObjects.isEmpty()) {
            return query(obj, indexName, WhereTypeQuery.UPDATE);
        }
        // Use the first object to generate the SQL
        CustomQuery templateQuery = query(batchOpObjects.get(0), indexName, WhereTypeQuery.UPDATE);
        String sql = templateQuery.getSql();
        List<List<Object>> tupleList = new ArrayList<>();
        for(Object batchObj : batchOpObjects) {
            CustomQuery cq = query(batchObj, indexName, WhereTypeQuery.UPDATE);
            tupleList.add(cq.getObjects());
        }
        CustomQuery batchQuery = new CustomQuery();
        batchQuery.setSql(sql);
        batchQuery.setTupleList(tupleList);
        return batchQuery;
    }

    /**
     * Create a query using IN clause for batch operations
     * @param obj Template object with annotations
     * @param batchValues List of values to include in the IN clause
     * @param inClauseKey The key for the IN clause field
     * @param whereTypeQuery Type of query (SELECT, UPDATE, DELETE)
     * @return CustomQuery with SQL and parameters
     * @throws Exception If reflection fails
     */
    public CustomQuery queryWithInClause(Object obj, List<Object> batchValues, String inClauseKey, WhereTypeQuery whereTypeQuery) throws Exception {
        final Table table = obj.getClass().getAnnotation(Table.class);
        String inClauseField = null;
        String inClauseColumnName = null;
        
        // Find the field with InClauseSupport annotation that matches our key
        for(Field field : obj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(InClauseSupport.class)) {
                InClauseSupport inClauseSupport = field.getAnnotation(InClauseSupport.class);
                if(inClauseSupport.keyValue().equals(inClauseKey)) {
                    inClauseField = field.getName();
                    inClauseColumnName = field.getAnnotation(ColumnName.class).value();
                    break;
                }
            }
        }
        
        if(inClauseField == null) {
            throw new IllegalArgumentException("No field found with InClauseSupport annotation for key: " + inClauseKey);
        }
        
        // Build the IN clause
        StringBuilder placeholders = new StringBuilder();
        for(int i = 0; i < batchValues.size(); i++) {
            if(i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        
        String sql = whereTypeQuery.getPrefix() + table.value() + 
                     " WHERE " + inClauseColumnName + " IN (" + placeholders.toString() + ")";
        
        // Create the CustomQuery with the SQL and parameters
        CustomQuery customQuery = new CustomQuery();
        customQuery.setSql(sql);
        customQuery.setObjects(batchValues);
        
        return customQuery;
    }



    private Boolean addPrimaryKeyInformation(String indexName, List<PrimaryKeyInformation> primaryKeyInformations,
                                                 Field field, Object columnVal, String columnName) throws Exception {
        if(field.isAnnotationPresent(PrimaryKey.class)) {
            PrimaryKey primaryKey = field.getAnnotation(PrimaryKey.class);
            Operation operation = field.getAnnotation(Operation.class);
            if (applyIndex(indexName, primaryKeyInformations, columnVal, columnName, primaryKey, operation)) {
                return true;
            }
        }
        if(field.isAnnotationPresent(PrimaryKeys.class)) {
            PrimaryKeys primaryKeys = field.getAnnotation(PrimaryKeys.class);
            Operation operation = field.getAnnotation(Operation.class);
            for(PrimaryKey primaryKey : primaryKeys.value()) {
                if(applyIndex(indexName, primaryKeyInformations, columnVal, columnName, primaryKey, operation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applyIndex(String indexName, List<PrimaryKeyInformation> primaryKeyInformations, Object columnVal,
                                      String columnName, PrimaryKey primaryKey, Operation operation) {
        if(indexName.equalsIgnoreCase(primaryKey.keyValue())) {
            PrimaryKeyInformation primaryKeyInformation = new PrimaryKeyInformation();
            primaryKeyInformation.setColumnName(columnName);
            primaryKeyInformation.setColumnValue(columnVal);
            primaryKeyInformation.setOrder(Integer.parseInt(primaryKey.order()));
            if(operation != null) {
                primaryKeyInformation.setOperation(operation.value());
            }
            primaryKeyInformations.add(primaryKeyInformation);
            return true;
        }
        return false;
    }

    public static enum WhereTypeQuery {
        SELECT("SELECT * FROM "),
        DELETE("DELETE FROM "),
        UPDATE("UPDATE ");

        private String prefix;
        WhereTypeQuery(String prefix) {
            this.prefix = prefix;
        }
        public String getPrefix() {
            return this.prefix;
        }
    }

    @Data
    private static class PrimaryKeyInformation {
        private String columnName;
        private Integer order;
        private Object columnValue;
        private String operation = "=";
    }
}
