package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A named type exposed by an {@link ExecutableSchema}.
 */
@ExperimentalApi
@NullMarked
public interface SchemaNamedType
        extends SchemaType, SchemaDirectiveContainer {
}
