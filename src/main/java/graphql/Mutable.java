package graphql;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * This marks a type as mutable which means after constructing it can be changed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface Mutable {
}
