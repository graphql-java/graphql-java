package graphql;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * An annotation that marks a method return value or method parameter as returning a duck type value.
 * <p>
 * For efficiency reasons, the graphql engine methods can return {@link Object} values
 * which maybe two well known types of values.  Often a {@link java.util.concurrent.CompletableFuture}
 * or a plain old {@link Object}, to represent an async value or a materialised value.
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {METHOD, PARAMETER})
public @interface DuckTyped {
    String shape();
}
