package graphql

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

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
        def result = new GraphQL(schema, '{ hello }').execute().result

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
        def result = new GraphQL(graphQLSchema, '{ simpson { id, name } }').execute().result

        then:
        result == [simpson: [id: '123', name: 'homer']]
    }

    @Ignore
    def "query with validation errors"() {
        given:
        GraphQLFieldDefinition fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .argument(GraphQLArgument.newArgument().name("arg").type(GraphQLString).build())
                .staticValue("world")
                .build()
        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .build()
        ).build()

        when:
        def errors = new GraphQL(schema, '{ hello(arg:11) }').execute().validationErrors

        then:
        result == [hello: 'world']


    }
}
