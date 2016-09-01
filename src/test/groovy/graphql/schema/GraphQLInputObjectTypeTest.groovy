package graphql.schema

import spock.lang.Specification

import graphql.AssertException

import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInputObjectField.newInputObjectField

import static graphql.Scalars.GraphQLString


class GraphQLInputObjectTypeTest extends Specification {

    def "duplicate field definition fails"() {
        when:
        newInputObject().name("TestInputObjectType")
                .field(newInputObjectField().name("NAME").type(GraphQLString))
                .field(newInputObjectField().name("NAME").type(GraphQLString))
                .build();
        then:
        thrown(AssertException)
    }
}
