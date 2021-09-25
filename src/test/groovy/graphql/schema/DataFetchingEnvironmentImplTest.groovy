package graphql.schema

import graphql.GraphQLContext
import graphql.cachecontrol.CacheControl
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.language.FragmentDefinition
import graphql.language.OperationDefinition
import graphql.language.TypeName
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.StarWarsSchema.starWarsSchema
import static graphql.TestUtil.toDocument
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment

class DataFetchingEnvironmentImplTest extends Specification {

    def frag = FragmentDefinition.newFragmentDefinition().name("frag").typeCondition(new TypeName("t")).build()

    def dataLoader = DataLoader.newDataLoader({ keys -> CompletableFuture.completedFuture(keys) } as BatchLoader)
    def operationDefinition = new OperationDefinition("q")
    def document = toDocument("{ f }")
    def executionId = ExecutionId.from("123")
    def fragmentByName = [frag: frag]
    def variables = [var: "able"]
    def dataLoaderRegistry = new DataLoaderRegistry().register("dataLoader", dataLoader)
    def cacheControl = CacheControl.newCacheControl()

    def executionContext = newExecutionContextBuilder()
            .root("root")
            .context("context")
            .graphQLContext(GraphQLContext.of(["key":"context"]))
            .executionId(executionId)
            .operationDefinition(operationDefinition)
            .document(document)
            .variables(variables)
            .graphQLSchema(starWarsSchema)
            .fragmentsByName(fragmentByName)
            .dataLoaderRegistry(dataLoaderRegistry)
            .cacheControl(cacheControl)
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
        then:
        dfe.getRoot() == "root"
        dfe.getContext() == "context"
        dfe.getGraphQlContext().get("key") == "context"
        dfe.getGraphQLSchema() == starWarsSchema
        dfe.getDocument() == document
        dfe.getVariables() == variables
        dfe.getOperationDefinition() == operationDefinition
        dfe.getExecutionId() == executionId
        dfe.getDataLoader("dataLoader") == dataLoader
    }

    def "create environment from existing one will copy everything to new instance"() {
        def dfe = newDataFetchingEnvironment()
                .context("Test Context")
                .graphQLContext(GraphQLContext.of(["key": "context"]))
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
                .cacheControl(cacheControl)
                .locale(Locale.CANADA)
                .localContext("localContext")
                .build()

        when:
        def dfeCopy = newDataFetchingEnvironment(dfe).build()

        then:
        dfe != dfeCopy
        dfe.getContext() == dfeCopy.getContext()
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
        dfe.getDataLoader("dataLoader") == dataLoader
        dfe.getCacheControl() == cacheControl
        dfe.getLocale() == dfeCopy.getLocale()
        dfe.getLocalContext() == dfeCopy.getLocalContext()
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
}
