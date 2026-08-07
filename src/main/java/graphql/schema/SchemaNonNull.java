package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A GraphQL non-null type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaNonNull
        extends SchemaModifiedType, SchemaInputType, SchemaOutputType {
}
