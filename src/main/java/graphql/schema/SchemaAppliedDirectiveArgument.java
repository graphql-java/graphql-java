package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

/**
 * An argument supplied to an applied directive.
 */
@ExperimentalApi
@NullMarked
public interface SchemaAppliedDirectiveArgument extends SchemaNamedElement {

    /**
     * Returns the declared type of this argument.
     *
     * @return the argument type
     */
    SchemaInputType getType();

    /**
     * Returns the value supplied to this argument.
     *
     * @return the supplied argument value
     */
    InputValueWithState getArgumentValue();
}
