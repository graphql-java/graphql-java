package graphql;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Marking a type explicitly as mutable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface Mutable {
}
