package graphql.execution.conversion;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;

/**
 * Contains the values passed to a {@link graphql.execution.conversion.ArgumentConverter}
 */
public interface ArgumentConverterEnvironment {

    /**
     * @return the argument being converted
     */
    GraphQLArgument getArgument();

    /**
     * @return the type of the argument
     */
    GraphQLInputType getArgumentType();

    /**
     * @return the source object to be converted
     */
    Object getSourceObject();
}
