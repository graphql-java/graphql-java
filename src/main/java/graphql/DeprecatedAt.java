package graphql;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Helper to track deprecation
 *
 * Please use ISO-8601 format i.e. YYYY-MM-DD
 */
@Retention(RetentionPolicy.SOURCE)
@Target(value = {CONSTRUCTOR, METHOD, TYPE, FIELD, PACKAGE})
public @interface DeprecatedAt {
    String value();
}
