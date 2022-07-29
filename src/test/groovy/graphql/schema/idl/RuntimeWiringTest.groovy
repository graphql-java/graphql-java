package graphql.schema.idl

import graphql.TypeResolutionEnvironment
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.TypeResolver
import graphql.schema.visibility.GraphqlFieldVisibility
import spock.lang.Specification

import java.util.function.UnaryOperator

class RuntimeWiringTest extends Specification {

    public static final Coercing coercing = new Coercing() {
        @Override
        Object serialize(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseValue(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }

        @Override
        Object parseLiteral(Object input) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    class NamedDF implements DataFetcher {
        String name

        NamedDF(String name) {
            this.name = name
        }

        @Override
        Object get(DataFetchingEnvironment environment) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    class NamedTR implements TypeResolver {
        String name

        NamedTR(String name) {
            this.name = name
        }

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            throw new UnsupportedOperationException("Not implemented")
        }
    }

    def "basic call structure"() {
        def wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", { type ->
            type
                    .dataFetcher("fieldX", new NamedDF("fieldX"))
                    .dataFetcher("fieldY", new NamedDF("fieldY"))
                    .dataFetcher("fieldZ", new NamedDF("fieldZ"))
                    .defaultDataFetcher(new NamedDF("defaultQueryDF"))
                    .typeResolver(new NamedTR("typeResolver4Query"))
        } as UnaryOperator<TypeRuntimeWiring.Builder>)

                .type("Mutation", { type ->
            type
                    .dataFetcher("fieldX", new NamedDF("mfieldX"))
                    .dataFetcher("fieldY", new NamedDF("mfieldY"))
                    .dataFetcher("fieldZ", new NamedDF("mfieldZ"))
                    .defaultDataFetcher(new NamedDF("defaultMutationDF"))
                    .typeResolver(new NamedTR("typeResolver4Mutation"))
        } as UnaryOperator<TypeRuntimeWiring.Builder>)
                .build()


        def fetchers = wiring.getDataFetchers()
        def resolvers = wiring.getTypeResolvers()

        expect:
        (fetchers.get("Query").get("fieldX") as NamedDF).name == "fieldX"
        (fetchers.get("Query").get("fieldY") as NamedDF).name == "fieldY"
        (fetchers.get("Query").get("fieldZ") as NamedDF).name == "fieldZ"
        (wiring.getDefaultDataFetcherForType("Query") as NamedDF).name == "defaultQueryDF"

        (resolvers.get("Query") as NamedTR).name == "typeResolver4Query"


        (fetchers.get("Mutation").get("fieldX") as NamedDF).name == "mfieldX"
        (fetchers.get("Mutation").get("fieldY") as NamedDF).name == "mfieldY"
        (fetchers.get("Mutation").get("fieldZ") as NamedDF).name == "mfieldZ"
        (wiring.getDefaultDataFetcherForType("Mutation") as NamedDF).name == "defaultMutationDF"

        (resolvers.get("Mutation") as NamedTR).name == "typeResolver4Mutation"
    }

    def "scalars are present"() {
        def customScalar = GraphQLScalarType.newScalar().name("URL").description("Custom").coercing(coercing).build()

        def wiring = RuntimeWiring.newRuntimeWiring().scalar(customScalar).build()

        expect:

        wiring.getScalars().get("URL").name == "URL"
        wiring.getScalars().get("URL") == customScalar

        wiring.getScalars().get("Int").name == "Int"
        wiring.getScalars().get("Float").name == "Float"
        wiring.getScalars().get("String").name == "String"
        wiring.getScalars().get("Boolean").name == "Boolean"
        wiring.getScalars().get("ID").name == "ID"
    }

    def "newRuntimeWiring works and copies values"() {
        when:
        def customScalar1 = GraphQLScalarType.newScalar()
                .name("Custom1").description("Custom 1").coercing(coercing).build()

        def oldWiring = RuntimeWiring.newRuntimeWiring()
                .scalar(customScalar1)
                .build()

        def customScalar2 = GraphQLScalarType.newScalar()
                .name("Custom2").description("Custom 2").coercing(coercing).build()

        GraphqlFieldVisibility fieldVisibility = new GraphqlFieldVisibility() {
            @Override
            List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
                return null
            }

            @Override
            GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
                return null
            }
        }

        def newWiring = RuntimeWiring.newRuntimeWiring(oldWiring)
                .scalar(customScalar2)
                .fieldVisibility(fieldVisibility)
                .build()

        then:
        newWiring.scalars.entrySet().containsAll(oldWiring.scalars.entrySet())
        newWiring.scalars["Custom1"] == customScalar1
        newWiring.scalars["Custom2"] == customScalar2
        newWiring.fieldVisibility == fieldVisibility
    }

    def "transform works and copies values"() {
        when:
        def customScalar1 = GraphQLScalarType.newScalar()
                .name("Custom1").description("Custom 1").coercing(coercing).build()

        def oldWiring = RuntimeWiring.newRuntimeWiring()
                .scalar(customScalar1)
                .build()

        def customScalar2 = GraphQLScalarType.newScalar()
                .name("Custom2").description("Custom 2").coercing(coercing).build()

        GraphqlFieldVisibility fieldVisibility = new GraphqlFieldVisibility() {
            @Override
            List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
                return null
            }

            @Override
            GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
                return null
            }
        }

        def newWiring = oldWiring.transform({ builder ->
            builder
                    .scalar(customScalar2)
                    .fieldVisibility(fieldVisibility)
        })

        then:
        newWiring.scalars.entrySet().containsAll(oldWiring.scalars.entrySet())
        newWiring.scalars["Custom1"] == customScalar1
        newWiring.scalars["Custom2"] == customScalar2
        newWiring.fieldVisibility == fieldVisibility
    }
}
