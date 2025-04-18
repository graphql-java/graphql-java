package graphql.schema.idl

import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.TypeResolver
import graphql.schema.idl.errors.StrictModeWiringException
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

    def "strict mode, on by default, can stop certain redefinitions"() {
        DataFetcher DF1 = env -> "x"
        DataFetcher DF2 = env -> "x"
        TypeResolver TR1 = env -> null
        EnumValuesProvider EVP1 = name -> null

        when:
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF1)) // Cannot redefine the same field's datafetcher

        then:
        def e1 = thrown(StrictModeWiringException)
        e1.message == "The field foo on type Foo has already been defined"

        when:
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))

        then:
        def e2 = thrown(StrictModeWiringException)
        e2.message == "The type Foo already has a type resolver defined"

        when:
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))
        then:
        def e3 = thrown(StrictModeWiringException)
        e3.message == "The type Foo already has a enum provider defined"

        when:
        RuntimeWiring.newRuntimeWiring()
                .scalar(Scalars.GraphQLString)
        then:
        def e4 = thrown(StrictModeWiringException)
        e4.message == "The scalar String is already defined"

        when:
        TypeRuntimeWiring.newTypeWiring("Foo")
                .defaultDataFetcher(DF1)
                .defaultDataFetcher(DF2)

        then:
        def e5 = thrown(StrictModeWiringException)
        e5.message == "The type Foo has already has a default data fetcher defined"
    }

    def "strict mode, on by default, permits a type to be defined more than once as long as elements are not overlapping"() {
        DataFetcher DF1 = env -> "x"
        DataFetcher DF2 = env -> "x"
        TypeResolver TR1 = env -> null
        EnumValuesProvider EVP1 = name -> null

        when:
        // Permit type wiring to be defined more than once, if child DataFetchers are for distinct fields
        def runtimeWiring1 = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("bar", DF2))
                .build()

        then:
        noExceptionThrown()
        runtimeWiring1.getDataFetchers().get("Foo").get("foo") == DF1
        runtimeWiring1.getDataFetchers().get("Foo").get("bar") == DF2

        when:
        // Only one type wiring is allowed per type, do not allow redefinition
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))

        then:
        def e2 = thrown(StrictModeWiringException)
        e2.message == "The type Foo already has a type resolver defined"

        when:
        // Only one enum values provider is allowed per type, do not allow redefinition
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))

        then:
        def e3 = thrown(StrictModeWiringException)
        e3.message == "The type Foo already has a enum provider defined"

        when:
        // Only one scalar wiring is allowed per scalar
        RuntimeWiring.newRuntimeWiring()
                .scalar(Scalars.GraphQLString)
        then:
        def e4 = thrown(StrictModeWiringException)
        e4.message == "The scalar String is already defined"

        when:
        // Only one default data fetcher is allowed, do not allow redefinition
        TypeRuntimeWiring.newTypeWiring("Foo")
                .defaultDataFetcher(DF1)
                .defaultDataFetcher(DF2)

        then:
        def e5 = thrown(StrictModeWiringException)
        e5.message == "The type Foo has already has a default data fetcher defined"
    }

    def "strict mode, if set to off, won't stop certain redefinitions"() {
        DataFetcher DF1 = env -> "x"
        DataFetcher DF2 = env -> "x"
        TypeResolver TR1 = env -> null
        EnumValuesProvider EVP1 = name -> null

        when:
        def runtimeWiring1 = RuntimeWiring.newRuntimeWiring()
                .strictMode(false)
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF2))
                .build()

        then:
        noExceptionThrown()
        runtimeWiring1.getDataFetchers().get("Foo").get("foo") == DF2

        when:
        def runtimeWiring2 = RuntimeWiring.newRuntimeWiring()
                .strictMode(false)
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").typeResolver(TR1))
                .build()

        then:
        noExceptionThrown()
        runtimeWiring2.typeResolvers.get("Foo") == TR1

        when:
        def runtimeWiring3 = RuntimeWiring.newRuntimeWiring()
                .strictMode(false)
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").enumValues(EVP1))
                .build()

        then:
        noExceptionThrown()
        runtimeWiring3.getEnumValuesProviders().get("Foo") == EVP1

        when:
        def runtimeWiring4 = RuntimeWiring.newRuntimeWiring()
                .strictMode(false)
                .scalar(Scalars.GraphQLString)
                .build()

        then:
        noExceptionThrown()
        runtimeWiring4.scalars.get("String") == Scalars.GraphQLString

        when:
        def typeRuntimeWiring = TypeRuntimeWiring.newTypeWiring("Foo")
                .strictMode(false)
                .defaultDataFetcher(DF1)
                .defaultDataFetcher(DF2)
                .build()

        then:
        noExceptionThrown()
        typeRuntimeWiring.defaultDataFetcher == DF2
    }

    def "when strict mode on, do not allow default data fetcher redefinition"() {
        DataFetcher DF1 = env -> "w"
        DataFetcher DEFAULT_DF = env -> "x"
        DataFetcher DEFAULT_DF2 = env -> "y"

        // Having a datafetcher and a default for the type is ok
        when:
        def runtimeWiring1 = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").defaultDataFetcher(DEFAULT_DF))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").dataFetcher("foo", DF1))
                .build()

        then:
        runtimeWiring1.getDefaultDataFetcherForType("Foo") == DEFAULT_DF

        // Do not permit redefinition of the default datafetcher
        when:
        RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Foo").defaultDataFetcher(DEFAULT_DF))
                .type(TypeRuntimeWiring.newTypeWiring("Foo").defaultDataFetcher(DEFAULT_DF2))
                .build()

        then:
        def error = thrown(StrictModeWiringException)
        error.message == "The type Foo already has a default data fetcher defined"
    }
}
