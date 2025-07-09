package graphql;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * This represents code that the graphql-java project considers experimental API and while our intention is that it will
 * progress to be {@link PublicApi}, its existence, signature or behavior may change between releases.
 * <p>
 * In general unnecessary changes will be avoided, but you should not depend on experimental classes being stable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {CONSTRUCTOR, METHOD, TYPE, FIELD})
@Documented
public @interface ExperimentalApi {
    /**
     * The key that should be associated with a boolean value which indicates whether @defer and @stream behaviour is enabled for this execution.
     */
    String ENABLE_INCREMENTAL_SUPPORT  = "ENABLE_INCREMENTAL_SUPPORT";

    /**
     * The key that should be associated with a boolean value which indicates whether normalized document behaviour is enabled for this execution.
     */
    String ENABLE_NORMALIZED_DOCUMENT_SUPPORT  = "ENABLE_NORMALIZED_DOCUMENT_SUPPORT";
}
