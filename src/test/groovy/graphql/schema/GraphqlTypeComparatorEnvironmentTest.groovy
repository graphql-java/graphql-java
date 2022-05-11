package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.schema.GraphqlTypeComparatorEnvironment.newEnvironment

class GraphqlTypeComparatorEnvironmentTest extends Specification {

    def "valid instance"() {
        when:
        environment.build()

        then:
        notThrown()

        where:

        environment << [
                newEnvironment()
                        .parentType(GraphQLObjectType.class)
                        .elementType(GraphQLFieldDefinition.class),
                newEnvironment()
                        .elementType(GraphQLFieldDefinition.class)
        ]
    }

    def "equals handles optional parentType"() {
        given:
        def fullEnvironmentA = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()
        def fullEnvironmentB = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()

        def partialEnvironmentA = newEnvironment()
                .elementType(GraphQLFieldDefinition.class)
                .build()
        def partialEnvironmentB = newEnvironment()
                .elementType(GraphQLFieldDefinition.class)
                .build()
        expect:
        fullEnvironmentA == fullEnvironmentB
        partialEnvironmentA == partialEnvironmentB
        fullEnvironmentA != partialEnvironmentA
        fullEnvironmentB != partialEnvironmentB
    }

    def "hashCode handles optional parentType"() {
        given:
        def fullEnvironmentA = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()
        def fullEnvironmentB = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()

        def partialEnvironmentA = newEnvironment()
                .elementType(GraphQLFieldDefinition.class)
                .build()
        def partialEnvironmentB = newEnvironment()
                .elementType(GraphQLFieldDefinition.class)
                .build()
        expect:
        fullEnvironmentA.hashCode() == fullEnvironmentB.hashCode()
        partialEnvironmentA.hashCode() == partialEnvironmentB.hashCode()
        fullEnvironmentA.hashCode() != partialEnvironmentA.hashCode()
        fullEnvironmentB.hashCode() != partialEnvironmentB.hashCode()
    }

    def "new environment can be created from an existing one"() {
        given:
        def startEnvironment = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()

        when:
        def nextEnvironment = newEnvironment(startEnvironment)

        then:
        startEnvironment.elementType == nextEnvironment.build().elementType
        startEnvironment.parentType == nextEnvironment.build().parentType
    }

    def "object can be transformed into a valid state"() {
        given:
        def startEnvironment = newEnvironment()
                .parentType(GraphQLObjectType.class)
                .elementType(GraphQLFieldDefinition.class)
                .build()
        when:
        def transformedEnvironment = startEnvironment.transform({
            it.parentType(null)
        })

        then:
        startEnvironment.parentType == GraphQLObjectType.class
        startEnvironment.elementType == GraphQLFieldDefinition.class

        transformedEnvironment.parentType == null
        transformedEnvironment.elementType == GraphQLFieldDefinition.class

        when:
        transformedEnvironment = startEnvironment.transform({
            it.elementType(null)
        })

        then:
        transformedEnvironment.parentType == GraphQLObjectType.class
        transformedEnvironment.elementType == null
    }

}
