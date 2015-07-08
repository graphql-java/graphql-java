package graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class GraphQLTest extends Specification {


    def "simple query"() {
        given:
        GraphQLFieldDefinition fieldDefinition = new GraphQLFieldDefinition("hello", GraphQLString, "world");
        GraphQLObjectType queryType = new GraphQLObjectType("RootQueryType", [fieldDefinition])
        GraphQLSchema graphQLSchema = new GraphQLSchema(queryType)

        when:
        def result = new GraphQL(graphQLSchema, '{ hello }').execute()

        then:
        result == [hello: 'world']

    }

    def "query with sub-fields"() {
        given:
        GraphQLFieldDefinition idFieldDefinition = new GraphQLFieldDefinition("id", GraphQLString);
        GraphQLFieldDefinition nameFieldDefinition = new GraphQLFieldDefinition("name", GraphQLString);
        GraphQLObjectType heroType = new GraphQLObjectType("heroType", [idFieldDefinition, nameFieldDefinition])
        GraphQLFieldDefinition simpsonField = new GraphQLFieldDefinition('simpson', heroType, [id: '123', name: 'homer'])
        GraphQLObjectType queryType = new GraphQLObjectType("RootQueryType", [simpsonField])

        GraphQLSchema graphQLSchema = new GraphQLSchema(queryType)

        when:
        def result = new GraphQL(graphQLSchema, '{ simpson { id, name } }').execute()

        then:
        result == [simpson: [id: '123', name: 'homer']]
    }
}
