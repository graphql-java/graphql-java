package graphql.schema

import spock.lang.Specification

import graphql.AssertException

import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

import static graphql.Scalars.GraphQLString


class GraphQLObjectTypeTest extends Specification {

    def "duplicate field definition fails"() {
        when:
        newObject().name("TestObjectType")
                .field(newFieldDefinition().name("NAME").type(GraphQLString))
                .field(newFieldDefinition().name("NAME").type(GraphQLString))
                .build();
        then:
        thrown(AssertException)
    }
}
