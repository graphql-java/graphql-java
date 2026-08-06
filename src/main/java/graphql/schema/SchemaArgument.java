package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * An argument declared by a field or directive.
 */
@ExperimentalApi
@NullMarked
public interface SchemaArgument extends SchemaDirectiveContainer {

    SchemaInputType getType();

    InputValueWithState getArgumentDefaultValue();
}
