package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A GraphQL scalar type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaScalar
        extends SchemaNamedType, SchemaInputType, SchemaOutputType {
}
