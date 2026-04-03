package graphql.schema

import graphql.AssertException
import graphql.Scalars
import graphql.TypeResolutionEnvironment
import spock.lang.Specification

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class CodeRegistryVisitorTest extends Specification {

    static TypeResolver dummyTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            return null
        }
    }

    def "schema build fails when a datafetcher is registered on an interface type field"() {
        given:
        def interfaceType = newInterface()
                .name("Animal")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .build()

        def dogType = newObject()
                .name("Dog")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("animal").type(interfaceType))
                .build()

        DataFetcher<?> badDf = { env -> "should not be here" }

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("Animal", "name"), badDf)
                .typeResolver("Animal", dummyTypeResolver)
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(dogType)
                .codeRegistry(codeRegistry)
                .build()

        then:
        def e = thrown(AssertException)
        e.message.contains("Animal")
        e.message.contains("name")
        e.message.contains("MUST NOT register a DataFetcher on the interface type")
    }

    def "schema build succeeds when datafetchers are registered on concrete types only"() {
        given:
        def interfaceType = newInterface()
                .name("Animal")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .build()

        def dogType = newObject()
                .name("Dog")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("animal").type(interfaceType))
                .build()

        DataFetcher<?> goodDf = { env -> "woof" }

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("Dog", "name"), goodDf)
                .typeResolver("Animal", dummyTypeResolver)
                .build()

        when:
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(dogType)
                .codeRegistry(codeRegistry)
                .build()

        then:
        noExceptionThrown()
        schema != null
    }

    def "schema build fails when a datafetcher is registered on multiple interface fields"() {
        given:
        def interfaceType = newInterface()
                .name("Vehicle")
                .field(newFieldDefinition().name("speed").type(Scalars.GraphQLInt))
                .field(newFieldDefinition().name("color").type(Scalars.GraphQLString))
                .build()

        def carType = newObject()
                .name("Car")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("speed").type(Scalars.GraphQLInt))
                .field(newFieldDefinition().name("color").type(Scalars.GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("vehicle").type(interfaceType))
                .build()

        DataFetcher<?> badDf = { env -> "bad" }

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("Vehicle", "speed"), badDf)
                .typeResolver("Vehicle", dummyTypeResolver)
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(carType)
                .codeRegistry(codeRegistry)
                .build()

        then:
        def e = thrown(AssertException)
        e.message.contains("Vehicle")
        e.message.contains("speed")
    }

    def "schema build succeeds with interfaces that have no datafetchers registered"() {
        given:
        def interfaceType = newInterface()
                .name("Node")
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString))
                .build()

        def userType = newObject()
                .name("User")
                .withInterface(interfaceType)
                .field(newFieldDefinition().name("id").type(Scalars.GraphQLString))
                .build()

        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition().name("node").type(interfaceType))
                .build()

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver("Node", dummyTypeResolver)
                .build()

        when:
        def schema = GraphQLSchema.newSchema()
                .query(queryType)
                .additionalType(userType)
                .codeRegistry(codeRegistry)
                .build()

        then:
        noExceptionThrown()
        schema != null
    }
}
