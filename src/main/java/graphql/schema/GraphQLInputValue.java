package graphql.schema;

import graphql.PublicApi;

/**
 * Named schema elements that contain input value type information and directivesvvcccckddfdngndbrvcfgtjrvkkdgbhjirnldfjhlvbu
 * .
 *
 * @see graphql.schema.GraphQLInputType
 * @see graphql.schema.GraphQLInputObjectField
 * @see graphql.schema.GraphQLArgument
 */
@PublicApi
public interface GraphQLInputValue extends GraphQLDirectiveContainer {

    <T extends GraphQLInputType> T getType();
}
