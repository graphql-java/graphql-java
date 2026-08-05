package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * An argument declared by a field or directive.
 */
@ExperimentalApi
@NullMarked
public interface SchemaArgument {

    String getName();

    SchemaInputType getType();

    InputValueWithState getArgumentDefaultValue();
}
