package graphql.schema;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

/**
 * Named schema elements that contain input type information.
 *
 *
 * @see graphql.schema.GraphQLInputType
 * @see graphql.schema.GraphQLInputObjectField
 * @see graphql.schema.GraphQLArgument
 */
@PublicApi
@NullMarked
public interface GraphQLInputValueDefinition extends GraphQLDirectiveContainer, GraphQLInputSchemaElement {

    <T extends GraphQLInputType> T getType();
}
