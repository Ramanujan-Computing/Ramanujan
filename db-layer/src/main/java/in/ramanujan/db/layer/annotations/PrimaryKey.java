package in.ramanujan.db.layer.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(PrimaryKeys.class)
public @interface PrimaryKey {
    public String keyValue();
    public String order();
}
