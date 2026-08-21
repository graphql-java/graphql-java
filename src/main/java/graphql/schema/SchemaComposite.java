package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A named output type that can own a selection set.
 */
@ExperimentalApi
@NullMarked
public interface SchemaComposite extends SchemaNamedType, SchemaOutputType {
}
