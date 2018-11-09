package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.Scalars
import graphql.TypeResolutionEnvironment
import graphql.schema.visibility.GraphqlFieldVisibility
import spock.lang.Specification

class GraphQLCodeRegistryTest extends Specification {

    class NamedDF implements DataFetcher {
        String name

        @Override
        Object get(DataFetchingEnvironment environment) throws Exception {
            return name
        }
    }

    class NamedTypeResolver implements TypeResolver {
        String name

        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            return objectType(name)
        }
    }

    class NamedFieldVisibility implements GraphqlFieldVisibility {
        String name

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
        return GraphQLFieldDefinition.newFieldDefinition().name(name).type(Scalars.GraphQLString).build()
    }

    static GraphQLObjectType objectType(String name) {
        return GraphQLObjectType.newObject().name(name).build()
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
                .dataFetcher(objectType("parentTyoe1"), field("A"), new NamedDF(name: "A"))
                .dataFetcher(objectType("parentTyoe2"), field("B"), new NamedDF(name: "B"))
                .dataFetcher(interfaceType("interfaceType1"), field("C"), new NamedDF(name: "C"))
                .dataFetchers(objectType("parentTyoe3"), [
                fieldD: new NamedDF(name: "D"),
                fieldE: new NamedDF(name: "E"),
        ])

        // we can do a read on a half built code registry, namely for schema directive wiring use cases
        then:
        (codeRegistryBuilder.getDataFetcher(objectType("parentTyoe1"), field("A")) as NamedDF).name == "A"
        (codeRegistryBuilder.getDataFetcher(objectType("parentTyoe2"), field("B")) as NamedDF).name == "B"
        (codeRegistryBuilder.getDataFetcher(interfaceType("interfaceType1"), field("C")) as NamedDF).name == "C"
        (codeRegistryBuilder.getDataFetcher(interfaceType("interfaceType1"), field("C")) as NamedDF).name == "C"
        (codeRegistryBuilder.getDataFetcher(objectType("parentTyoe3"), field("fieldD")) as NamedDF).name == "D"
        (codeRegistryBuilder.getDataFetcher(objectType("parentTyoe3"), field("fieldE")) as NamedDF).name == "E"

        codeRegistryBuilder.getDataFetcher(objectType("parentTyoe2"), field("A")) instanceof PropertyDataFetcher // a default one

        when:
        def codeRegistry = codeRegistryBuilder.build()
        then:
        (codeRegistry.getDataFetcher(objectType("parentTyoe1"), field("A")) as NamedDF).name == "A"
        (codeRegistry.getDataFetcher(objectType("parentTyoe2"), field("B")) as NamedDF).name == "B"
        (codeRegistry.getDataFetcher(interfaceType("interfaceType1"), field("C")) as NamedDF).name == "C"
        (codeRegistry.getDataFetcher(objectType("parentTyoe3"), field("fieldD")) as NamedDF).name == "D"
        (codeRegistry.getDataFetcher(objectType("parentTyoe3"), field("fieldE")) as NamedDF).name == "E"

        codeRegistry.getDataFetcher(objectType("parentTyoe2"), field("A")) instanceof PropertyDataFetcher // a default one

    }

    def "records type resolvers against unions and interfaces"() {
        when:
        def codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(interfaceType("interfaceType1"), new NamedTypeResolver(name: "A"))
                .typeResolver(interfaceType("interfaceType2"), new NamedTypeResolver(name: "B"))
                .typeResolver(unionType("unionType1"), new NamedTypeResolver(name: "C"))

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
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(new NamedFieldVisibility(name: "A")).build()
        then:
        (codeRegistry.getFieldVisibility() as NamedFieldVisibility).name == "A"
    }

    def "schema delegates field visibility to code registry"() {

        when:
        def schema = GraphQLSchema.newSchema().fieldVisibility(new NamedFieldVisibility(name: "B")).query(objectType("query")).build()
        then:
        (schema.getFieldVisibility() as NamedFieldVisibility).name == "B"
        (schema.getCodeRegistry().getFieldVisibility() as NamedFieldVisibility).name == "B"
    }

    def "integration test that code registry gets asked for data fetchers"() {

        def queryType = GraphQLObjectType.newObject().name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition().name("codeRegistryField").type(Scalars.GraphQLString))
                .field(GraphQLFieldDefinition.newFieldDefinition().name("nonCodeRegistryField").type(Scalars.GraphQLString)
        // df comes from the field itself here
                .dataFetcher(new NamedDF(name: "nonCodeRegistryFieldValue")))
                .build()

        // here we wire in a specific data fetcher via the code registry
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetchers("Query", [codeRegistryField: new NamedDF(name: "codeRegistryFieldValue")])
                .build()

        def schema = GraphQLSchema.newSchema().query(queryType).codeRegistry(codeRegistry).build()
        def graphQL = GraphQL.newGraphQL(schema).build()
        when:
        def er = graphQL.execute(ExecutionInput.newExecutionInput().query('''
            query {
                codeRegistryField, nonCodeRegistryField
            }
            ''').build())
        then:
        er.errors.isEmpty()
        er.data == [codeRegistryField: "codeRegistryFieldValue", nonCodeRegistryField: "nonCodeRegistryFieldValue"]

    }
}
