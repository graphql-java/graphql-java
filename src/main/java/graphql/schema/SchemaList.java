package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A GraphQL list type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaList
        extends SchemaModifiedType, SchemaInputType, SchemaOutputType {
}
