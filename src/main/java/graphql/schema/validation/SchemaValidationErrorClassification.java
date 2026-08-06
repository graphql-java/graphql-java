package graphql.schema.validation;


import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;


/**
 * Error in graphql schema validation can have a classification,
 * and all the error classifications implement this interface.
 */
@PublicApi
@NullMarked
public interface SchemaValidationErrorClassification {

}
