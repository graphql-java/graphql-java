package graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.ResolveValue
import spock.lang.Specification

import static graphql.Scalars.GraphQLString


class GraphQLTest extends Specification {


    def "simple query"() {
        given:
        GraphQLFieldDefinition fieldDefinition = new GraphQLFieldDefinition("hello", GraphQLString, null, new ResolveValue() {
            @Override
            Object resolve() {
                return "world";
            }
        })
        GraphQLObjectType graphQLObjectType = new GraphQLObjectType("RootQueryType", ['hello': fieldDefinition])
        GraphQLSchema graphQLSchema = new GraphQLSchema()
        graphQLSchema.queryType = graphQLObjectType

        when:
        def result = new GraphQL(graphQLSchema, '{ hello }').execute()

        then:
        result == ["hello": "world"]

    }
}
