package graphql.execution.conversion;

import graphql.PublicApi;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;

/**
 * Contains the values passed to a {@link graphql.execution.conversion.ArgumentConverter}
 */
@PublicApi
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
     * @return the argument value to be converted
     */
    Object getValueToBeConverted();
}
