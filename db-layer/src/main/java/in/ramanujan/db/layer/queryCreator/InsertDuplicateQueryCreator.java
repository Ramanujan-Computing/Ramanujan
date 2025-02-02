package in.ramanujan.db.layer.queryCreator;

import in.ramanujan.db.layer.annotations.ColumnName;

import java.lang.reflect.Field;
import java.util.List;

public class InsertDuplicateQueryCreator extends InsertQueryCreator {
    @Override
    public CustomQuery query(Object obj, List<Object> batchOpObjs) throws Exception {
        CustomQuery customQuery = super.query(obj, batchOpObjs);
        ColumnName duplicateSeparator = null;
        for(Field field : obj.getClass().getDeclaredFields()) {
            if(field.isAnnotationPresent(ColumnName.class)) {
                duplicateSeparator = ((ColumnName)field.getAnnotation(ColumnName.class));
                if(duplicateSeparator.duplicateSeparator()) {
                    break;
                }
                duplicateSeparator = null;
            }
        }
        if(duplicateSeparator != null) {
            String attachment = " ON DUPLICATE KEY UPDATE " + duplicateSeparator.value() + " = VALUES(" + duplicateSeparator.value() + ")";
            customQuery.setSql(customQuery.getSql() + attachment);
        }
        return customQuery;
    }
}
