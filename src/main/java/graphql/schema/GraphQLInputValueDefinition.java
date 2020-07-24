package graphql.schema;

import graphql.PublicApi;

/**
 * Named schema elements that contain input type information.
 *
 *
 * @see graphql.schema.GraphQLInputType
 * @see graphql.schema.GraphQLInputObjectField
 * @see graphql.schema.GraphQLArgument
 */
@PublicApi
public interface GraphQLInputValueDefinition extends GraphQLDirectivesContainer {

    <T extends GraphQLInputType> T getType();
}
