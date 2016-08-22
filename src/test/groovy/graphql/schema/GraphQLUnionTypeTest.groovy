package graphql.schema

import spock.lang.Specification

import graphql.AssertException
import graphql.schema.TypeResolverProxy

import static graphql.schema.GraphQLUnionType.newUnionType

import static graphql.Scalars.GraphQLString


class GraphQLUnionTypeTest extends Specification {

    def "no possible types in union fails"() {
        when:
        newUnionType()
                .name("TestUnionType")
                .typeResolver(new TypeResolverProxy())
                .build();
        then:
        thrown(AssertException)
    }
}
