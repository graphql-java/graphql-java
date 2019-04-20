package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.TypeResolutionEnvironment
import graphql.schema.visibility.GraphqlFieldVisibility
import spock.lang.Specification

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class GraphQLCodeRegistryTest extends Specification {

    class NamedDF implements DataFetcher {
        String name

        NamedDF(name) {
            this.name = name
        }

        @Override
        Object get(DataFetchingEnvironment environment) throws Exception {
            return name
        }
    }

    class NamedTypeResolver implements TypeResolver {
        String name

        NamedTypeResolver(name) {
            this.name = name
        }

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            return objectType(name)
        }
    }

    class NamedFieldVisibility implements GraphqlFieldVisibility {
        String name

        NamedFieldVisibility(name) {
            this.name = name
        }

        @Override
        List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
            return fieldsContainer.fieldDefinitions
        }

        @Override
        GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
            return fieldsContainer.getFieldDefinition(fieldName)
        }
    }

    static GraphQLFieldDefinition field(String name) {
        return newFieldDefinition().name(name).type(Scalars.GraphQLString).build()
    }

    static GraphQLObjectType objectType(String name) {
        return newObject().name(name).build()
    }

    static GraphQLInterfaceType interfaceType(String name) {
        return GraphQLInterfaceType.newInterface().name(name).build()
    }

    static GraphQLUnionType unionType(String name) {
        return GraphQLUnionType.newUnionType().name(name).possibleType(objectType("obj")).build()
    }

    def "records data fetchers against parent objects and fields"() {

        when:
        def codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates("parentType1", "A"), new NamedDF("A"))
                .dataFetcher(FieldCoordinates.coordinates("parentType2", "B"), new NamedDF("B"))
                .dataFetchers("parentType3", [fieldD: new NamedDF("D"), fieldE: new NamedDF("E")])

        // we can do a read on a half built code registry, namely for schema directive wiring use cases
        then:
        (codeRegistryBuilder.getDataFetcher(objectType("parentType1"), field("A")) as NamedDF).name == "A"
        (codeRegistryBuilder.getDataFetcher(objectType("parentType2"), field("B")) as NamedDF).name == "B"
        (codeRegistryBuilder.getDataFetcher(objectType("parentType3"), field("fieldD")) as NamedDF).name == "D"
        (codeRegistryBuilder.getDataFetcher(objectType("parentType3"), field("fieldE")) as NamedDF).name == "E"

        codeRegistryBuilder.getDataFetcher(objectType("parentType2"), field("A")) instanceof PropertyDataFetcher // a default one

        when:
        def codeRegistry = codeRegistryBuilder.build()
        then:
        (codeRegistry.getDataFetcher(objectType("parentType1"), field("A")) as NamedDF).name == "A"
        (codeRegistry.getDataFetcher(objectType("parentType2"), field("B")) as NamedDF).name == "B"
        (codeRegistry.getDataFetcher(objectType("parentType3"), field("fieldD")) as NamedDF).name == "D"
        (codeRegistry.getDataFetcher(objectType("parentType3"), field("fieldE")) as NamedDF).name == "E"

        codeRegistry.getDataFetcher(objectType("parentType2"), field("A")) instanceof PropertyDataFetcher // a default one

    }

    def "records type resolvers against unions and interfaces"() {
        when:
        def codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(interfaceType("interfaceType1"), new NamedTypeResolver("A"))
                .typeResolver(interfaceType("interfaceType2"), new NamedTypeResolver("B"))
                .typeResolver(unionType("unionType1"), new NamedTypeResolver("C"))

        then:
        (codeRegistryBuilder.getTypeResolver(interfaceType("interfaceType1")) as NamedTypeResolver).name == "A"
        (codeRegistryBuilder.getTypeResolver(interfaceType("interfaceType2")) as NamedTypeResolver).name == "B"
        (codeRegistryBuilder.getTypeResolver(unionType("unionType1")) as NamedTypeResolver).name == "C"

        when:
        def codeRegistry = codeRegistryBuilder.build()
        then:
        (codeRegistry.getTypeResolver(interfaceType("interfaceType1")) as NamedTypeResolver).name == "A"
        (codeRegistry.getTypeResolver(interfaceType("interfaceType2")) as NamedTypeResolver).name == "B"
        (codeRegistry.getTypeResolver(unionType("unionType1")) as NamedTypeResolver).name == "C"
    }

    def "records field visibility"() {

        when:
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(new NamedFieldVisibility("A")).build()
        then:
        (codeRegistry.getFieldVisibility() as NamedFieldVisibility).name == "A"
    }

    def "schema delegates field visibility to code registry"() {

        when:
        def schema = GraphQLSchema.newSchema().fieldVisibility(new NamedFieldVisibility("B")).query(objectType("query")).build()
        then:
        (schema.getFieldVisibility() as NamedFieldVisibility).name == "B"
        (schema.getCodeRegistry().getFieldVisibility() as NamedFieldVisibility).name == "B"
    }

    def "integration test that code registry gets asked for data fetchers"() {

        def queryType = newObject().name("Query")
                .field(newFieldDefinition().name("codeRegistryField").type(Scalars.GraphQLString))
                .field(newFieldDefinition().name("nonCodeRegistryField").type(Scalars.GraphQLString)
        // df comes from the field itself here
                .dataFetcher(new NamedDF("nonCodeRegistryFieldValue")))
                .field(newFieldDefinition().name("neitherSpecified").type(Scalars.GraphQLString))
                .build()

        // here we wire in a specific data fetcher via the code registry
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetchers("Query", [codeRegistryField: new NamedDF("codeRegistryFieldValue")])
                .build()

        def schema = GraphQLSchema.newSchema().query(queryType).codeRegistry(codeRegistry).build()
        def graphQL = GraphQL.newGraphQL(schema).build()
        when:
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query('''
            query {
                codeRegistryField, nonCodeRegistryField,neitherSpecified
            }
            ''').root([neitherSpecified: "neitherSpecifiedValue"]).build())
        then:
        er.errors.isEmpty()
        er.data == [codeRegistryField: "codeRegistryFieldValue", nonCodeRegistryField: "nonCodeRegistryFieldValue", neitherSpecified: "neitherSpecifiedValue"]

        // when nothing is specified then its a plain old PropertyDataFetcher
        schema.getCodeRegistry().getDataFetcher(queryType, queryType.getFieldDefinition("neitherSpecified")) instanceof PropertyDataFetcher

    }

    def "integration test that code registry works with SDL and the code registry can be pre-specified"() {

        def spec = '''
                type Query {
                    codeRegistryField : String
                    nonCodeRegistryField : String
                    neitherSpecified : String
                }
        '''

        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetchers("Query", [codeRegistryField: new NamedDF("codeRegistryFieldValue")]
        )
        def runtime = newRuntimeWiring().type(newTypeWiring("Query")
                .dataFetcher("nonCodeRegistryField", new NamedDF("nonCodeRegistryFieldValue")))
                .codeRegistry(codeRegistry)
                .build()
        def schema = TestUtil.schema(spec, runtime)
        def graphQL = GraphQL.newGraphQL(schema).build()
        when:
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query('''
            query {
                codeRegistryField, nonCodeRegistryField,neitherSpecified
            }
            ''').root([neitherSpecified: "neitherSpecifiedValue"]).build())
        then:
        er.errors.isEmpty()
        er.data == [codeRegistryField: "codeRegistryFieldValue", nonCodeRegistryField: "nonCodeRegistryFieldValue", neitherSpecified: "neitherSpecifiedValue"]

        // when nothing is specified then its a plain old PropertyDataFetcher
        def queryType = schema.getObjectType("Query")
        schema.getCodeRegistry().getDataFetcher(queryType, queryType.getFieldDefinition("neitherSpecified")) instanceof PropertyDataFetcher

    }
}
