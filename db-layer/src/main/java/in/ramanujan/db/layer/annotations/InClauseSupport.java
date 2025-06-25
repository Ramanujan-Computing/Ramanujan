package in.ramanujan.db.layer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field that can be used in SQL IN clause for batch operations
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InClauseSupport {
    /**
     * The name of the query key to associate with this field
     */
    public String keyValue();
}
