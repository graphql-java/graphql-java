package graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema

class Fixtures {


    static GraphQLSchema simpsonsSchema() {
        GraphQLFieldDefinition nameField = new GraphQLFieldDefinition("name", Scalars.GraphQLString)

        GraphQLObjectType simpsonCharacter = new GraphQLObjectType("simpsonCharacter", [nameField])
        GraphQLFieldDefinition simpsons = new GraphQLFieldDefinition("simpson", simpsonCharacter)
        GraphQLObjectType queryType = new GraphQLObjectType("query", [simpsons])
        GraphQLSchema schema = new GraphQLSchema(queryType)
        schema
    }
}
