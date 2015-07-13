package graphql

import graphql.schema.*

import static graphql.Scalars.GraphQLString


class TestUtil {


    static GraphQLSchema schemaWithInputType(GraphQLInputType inputType) {
        GraphQLFieldArgument fieldArgument = new GraphQLFieldArgument("arg", inputType)
        GraphQLFieldDefinition name = GraphQLFieldDefinition.newFieldDefinition()
                .name("name").type(GraphQLString).argument(fieldArgument).build()
        GraphQLObjectType queryType = GraphQLObjectType.newObject().name("query").field(name).build()
        new GraphQLSchema(queryType)
    }
}
