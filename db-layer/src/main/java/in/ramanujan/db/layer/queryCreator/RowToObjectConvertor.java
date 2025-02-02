package in.ramanujan.db.layer.queryCreator;

import in.ramanujan.db.layer.annotations.ColumnName;
import io.vertx.sqlclient.Row;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.Date;

public class RowToObjectConvertor {
    public static Object convert(Row row, Object sampleObj) throws Exception {
        Object toBeReturnedObj = sampleObj.getClass().newInstance();
        for(Field field : sampleObj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(ColumnName.class)) {
                String columnName = ((ColumnName)field.getAnnotation(ColumnName.class)).value();
                Object columnVal = row.getValue(columnName);
                setField(toBeReturnedObj, field, columnVal);
            }
        }
        return toBeReturnedObj;
    }

    public static Object convert(ResultSet row, Object sampleObj) throws Exception {
        Object toBeReturnedObj = sampleObj.getClass().newInstance();
        for(Field field : sampleObj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(ColumnName.class)) {
                String columnName = ((ColumnName)field.getAnnotation(ColumnName.class)).value();
                Object columnVal = row.getObject(columnName);
                setField(toBeReturnedObj, field, columnVal);
            }
        }
        return toBeReturnedObj;
    }

    private static void setField(Object toBeReturnedObj, Field field, Object columnVal) throws IllegalAccessException {
        Class clazz = (Class) field.getGenericType();
        if(clazz == Object.class) {
            field.set(toBeReturnedObj, (columnVal));
        }
        if(clazz == String.class) {
            field.set(toBeReturnedObj, (String) columnVal);
        }
        if(clazz == Date.class) {
            field.set(toBeReturnedObj, (Date) columnVal);
        }
        if(clazz == Integer.class) {
            field.set(toBeReturnedObj, (Integer) columnVal);
        }
        if(clazz == Long.class) {
            field.set(toBeReturnedObj, (Long) columnVal);
        }
    }
}
