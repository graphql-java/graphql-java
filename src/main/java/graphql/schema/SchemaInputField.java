package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * A field declared by an input object type.
 */
@ExperimentalApi
@NullMarked
public interface SchemaInputField {

    String getName();

    SchemaInputType getType();

    InputValueWithState getInputFieldDefaultValue();
}
