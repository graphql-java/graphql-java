package graphql

import graphql.schema.GraphQLFieldArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema


class TestUtil {


    static GraphQLSchema schemaWithInputType(GraphQLInputType inputType) {
        GraphQLFieldArgument fieldArgument = new GraphQLFieldArgument("arg", inputType)
        GraphQLFieldDefinition name = new GraphQLFieldDefinition("name", Scalars.GraphQLString, [fieldArgument])
        GraphQLObjectType queryType = new GraphQLObjectType("query", [name])
        new GraphQLSchema(queryType)
    }
}
