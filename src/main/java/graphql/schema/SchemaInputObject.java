package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A GraphQL input object type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaInputObject
        extends SchemaInputFieldsContainer, SchemaInputType {

    boolean isOneOf();
}
