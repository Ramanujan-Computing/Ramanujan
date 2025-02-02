package in.ramanujan.db.layer.queryCreator;

import in.ramanujan.db.layer.annotations.ColumnName;
import in.ramanujan.db.layer.annotations.Table;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InsertQueryCreator {
    public CustomQuery query(Object obj, List<Object> batchOpObjs) throws Exception {
        final Table table = obj.getClass().getAnnotation(Table.class);
        Map<String, Object> columnNameValueMap = getColumnNameValueMap(obj);
        List<Object> objects = new ArrayList<>();
        String sql = "INSERT INTO " + table.value() + " (";
        int size = columnNameValueMap.size();
        List<String> columnNames = new ArrayList<>();
        for(String columnName : columnNameValueMap.keySet()) {
            columnNames.add(columnName);
            sql +=  columnName;
            if(size > 1) {
                sql += ",";
            }
            size--;
            objects.add(columnNameValueMap.get(columnName));
        }
        sql += ") VALUES (";
        for(int i = 0; i < objects.size(); i++) {
            sql +="?";
            if(i < (objects.size() - 1)) {
                sql += ",";
            }
        }
        sql += ")";

        CustomQuery customQuery = new CustomQuery();
        customQuery.setSql(sql);
        customQuery.setObjects(objects);
        if(batchOpObjs != null && batchOpObjs.size() > 0) {
            List<List<Object>> tuples = new ArrayList<>();
            for(Object batchPartObj : batchOpObjs) {
                Map<String, Object> batchPartObjColNameValMap = getColumnNameValueMap(batchPartObj);
                List<Object> parts = new ArrayList<>();
                for(String colName : columnNames) {
                    parts.add(batchPartObjColNameValMap.get(colName));
                }
                tuples.add(parts);
            }
            customQuery.setTupleList(tuples);
        }
        return customQuery;
    }

    private Map<String, Object> getColumnNameValueMap(Object obj) throws IllegalAccessException {
        Map<String, Object> columnNameValueMap = new HashMap<>();
        for(Field field : obj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(ColumnName.class)) {
                Object columnVal = field.get(obj);
                String columnName = ((ColumnName)field.getAnnotation(ColumnName.class)).value();
                columnNameValueMap.put(columnName, columnVal);
            }
        }
        return columnNameValueMap;
    }
}
