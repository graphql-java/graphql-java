package graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This represents code that the graphql-java project considers experimental API and has an NO imperative to be stable within
 * major or minor releases, and in fact may disappear at any time.  We probably intended to make this {@link graphql.PublicApi}
 * at some point but at this stage we are hedging our bets regarding this class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, TYPE})
@Documented
public @interface ExperimentalApi {
}
