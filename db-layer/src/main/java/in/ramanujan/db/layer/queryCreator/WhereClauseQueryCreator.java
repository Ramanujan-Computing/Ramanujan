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
