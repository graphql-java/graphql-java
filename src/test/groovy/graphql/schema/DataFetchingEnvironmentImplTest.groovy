package graphql.schema

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys
import graphql.language.Argument
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.SelectionSet
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.StarWarsSchema.starWarsSchema
import static graphql.TestUtil.mergedField
import static graphql.TestUtil.toDocument
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment

class DataFetchingEnvironmentImplTest extends Specification {

    def frag = FragmentDefinition.newFragmentDefinition().name("frag").typeCondition(new TypeName("t")).selectionSet(SelectionSet.newSelectionSet().build()).build()

    def dataLoader = DataLoaderFactory.newDataLoader({ keys -> CompletableFuture.completedFuture(keys) } as BatchLoader)
    def operationDefinition = new OperationDefinition("q")
    def document = toDocument("{ f }")
    def executionId = ExecutionId.from("123")
    def fragmentByName = [frag: frag]
    def variables = [var: "able"]
    def dataLoaderRegistry = new DataLoaderRegistry().register("dataLoader", dataLoader)

    def executionContext = newExecutionContextBuilder()
            .root("root")
            .graphQLContext(GraphQLContext.of(["key": "context"]))
            .executionId(executionId)
            .operationDefinition(operationDefinition)
            .document(document)
            .coercedVariables(CoercedVariables.of(variables))
            .graphQLSchema(starWarsSchema)
            .fragmentsByName(fragmentByName)
            .dataLoaderRegistry(dataLoaderRegistry)
            .build()

    def "immutable arguments"() {
        def dataFetchingEnvironment = newDataFetchingEnvironment(executionContext).arguments([arg: "argVal"])
                .build()

        when:
        def value = dataFetchingEnvironment.getArguments().get("arg")
        then:
        value == "argVal"
        when:
        dataFetchingEnvironment.getArguments().put("arg", "some other value")
        then:
        thrown(UnsupportedOperationException)
    }

    def "copying works as expected from execution context"() {
        when:
        def dfe = newDataFetchingEnvironment(executionContext)
                .build()
        dfe.getGraphQlContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, chainedDataLoaderEnabled)
        then:
        dfe.getRoot() == "root"
        dfe.getGraphQlContext().get("key") == "context"
        dfe.getGraphQLSchema() == starWarsSchema
        dfe.getDocument() == document
        dfe.getVariables() == variables
        dfe.getOperationDefinition() == operationDefinition
        dfe.getExecutionId() == executionId
        dfe.getDataLoaderRegistry() == executionContext.getDataLoaderRegistry()
        dfe.getDataLoader("dataLoader") == executionContext.getDataLoaderRegistry().getDataLoader("dataLoader") ||
                dfe.getDataLoader("dataLoader").delegate == executionContext.getDataLoaderRegistry().getDataLoader("dataLoader")
        where:
        chainedDataLoaderEnabled << [true, false]
    }

    def "create environment from existing one will copy everything to new instance"() {
        def dfe = newDataFetchingEnvironment()
                .context("Test Context") // Retain deprecated builder for coverage
                .graphQLContext(GraphQLContext.of(["key": "context", (DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): chainedDataLoaderEnabled]))
                .source("Test Source")
                .root("Test Root")
                .fieldDefinition(Mock(GraphQLFieldDefinition))
                .fieldType(Mock(GraphQLOutputType))
                .executionStepInfo(Mock(ExecutionStepInfo))
                .parentType(Mock(GraphQLType))
                .graphQLSchema(Mock(GraphQLSchema))
                .fragmentsByName(fragmentByName)
                .executionId(Mock(ExecutionId))
                .selectionSet(Mock(DataFetchingFieldSelectionSet))
                .operationDefinition(operationDefinition)
                .document(document)
                .variables(variables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .locale(Locale.CANADA)
                .localContext("localContext")
                .build()

        when:
        def dfeCopy = newDataFetchingEnvironment(dfe).build()

        then:
        dfe != dfeCopy
        dfe.getContext() == dfeCopy.getContext() // Retain deprecated method for coverage
        dfe.getGraphQlContext() == dfeCopy.getGraphQlContext()
        dfe.getSource() == dfeCopy.getSource()
        dfe.getRoot() == dfeCopy.getRoot()
        dfe.getFieldDefinition() == dfeCopy.getFieldDefinition()
        dfe.getFieldType() == dfeCopy.getFieldType()
        dfe.getExecutionStepInfo() == dfeCopy.getExecutionStepInfo()
        dfe.getParentType() == dfeCopy.getParentType()
        dfe.getGraphQLSchema() == dfeCopy.getGraphQLSchema()
        dfe.getFragmentsByName() == dfeCopy.getFragmentsByName()
        dfe.getExecutionId() == dfeCopy.getExecutionId()
        dfe.getSelectionSet() == dfeCopy.getSelectionSet()
        dfe.getDocument() == dfeCopy.getDocument()
        dfe.getOperationDefinition() == dfeCopy.getOperationDefinition()
        dfe.getVariables() == dfeCopy.getVariables()
        dfe.getDataLoader("dataLoader") == executionContext.getDataLoaderRegistry().getDataLoader("dataLoader") ||
                dfe.getDataLoader("dataLoader").delegate == dfeCopy.getDataLoader("dataLoader").delegate
        dfe.getLocale() == dfeCopy.getLocale()
        dfe.getLocalContext() == dfeCopy.getLocalContext()
        where:
        chainedDataLoaderEnabled << [true, false]

    }

    def "get or default support"() {
        when:
        def dfe = newDataFetchingEnvironment(executionContext)
                .arguments([x: "y"])
                .build()
        then:
        dfe.getArgument("z") == null
        dfe.getArgumentOrDefault("z", "default") == "default"
        dfe.getArgument("x") == "y"
        dfe.getArgumentOrDefault("x", "default") == "y"
    }

    def "deprecated getFields() method works"() {
        when:
        Argument argument = new Argument("arg1", new StringValue("argVal"))
        Field field = new Field("someField", [argument])

        def environment = newDataFetchingEnvironment(executionContext)
                .mergedField(mergedField(field))
                .build()

        then:
        environment.fields == [field] // Retain deprecated method for test coverage
    }

}
