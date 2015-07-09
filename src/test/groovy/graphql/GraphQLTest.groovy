package graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.*
import static graphql.schema.GraphQLObjectType.*
import static graphql.schema.GraphQLSchema.*

class GraphQLTest extends Specification {


    def "simple query"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .staticValue("world")
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build()
        ).build()

        when:
        def result = new GraphQL(schema, '{ hello }').execute()

        then:
        result == [hello: 'world']

    }

    def "query with sub-fields"() {
        given:
        GraphQLObjectType heroType = newObject()
                .name("heroType")
                .field(
                newFieldDefinition()
                        .name("id")
                        .type(GraphQLString)
                        .build())
                .field(
                newFieldDefinition()
                        .name("name")
                        .type(GraphQLString)
                        .build())
                .build()

        GraphQLFieldDefinition simpsonField = newFieldDefinition()
                .name("simpson")
                .type(heroType)
                .staticValue([id: '123', name: 'homer']).build()

        GraphQLSchema graphQLSchema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(simpsonField)
                        .build()
        ).build();

        when:
        def result = new GraphQL(graphQLSchema, '{ simpson { id, name } }').execute()

        then:
        result == [simpson: [id: '123', name: 'homer']]
    }
}
