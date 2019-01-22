package graphql.schema


import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.language.FragmentDefinition
import graphql.language.IgnoredChars
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

    def frag = new FragmentDefinition("frag", new TypeName("t"), [], null, null, [], new IgnoredChars([], []))

    def dataLoader = DataLoader.newDataLoader({ keys -> CompletableFuture.completedFuture(keys) } as BatchLoader)
    def operationDefinition = new OperationDefinition("q")
    def document = toDocument("{ f }")
    def executionId = ExecutionId.from("123")
    def fragmentByName = [frag: frag]
    def variables = [var: "able"]
    def dataLoaderRegistry = new DataLoaderRegistry().register("dataLoader", dataLoader)

    def executionContext = newExecutionContextBuilder()
            .root("root")
            .context("context")
            .executionId(executionId)
            .operationDefinition(operationDefinition)
            .document(document)
            .variables(variables)
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
        value = dataFetchingEnvironment.getArguments().get("arg")
        then:
        value == "argVal"
    }

    def "copying works as expected from execution context"() {

        when:
        def dfe = newDataFetchingEnvironment(executionContext)
                .build()
        then:
        dfe.getRoot() == "root"
        dfe.getContext() == "context"
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
                .source("Test Source")
                .root("Test Root")
                .fieldDefinition(Mock(GraphQLFieldDefinition))
                .fieldType(Mock(GraphQLOutputType))
                .executionStepInfo(Mock(ExecutionStepInfo))
                .parentType(Mock(GraphQLType))
                .graphQLSchema(Mock(GraphQLSchema))
                .fragmentsByName(Mock(Map))
                .executionId(Mock(ExecutionId))
                .selectionSet(Mock(DataFetchingFieldSelectionSet))
                .operationDefinition(operationDefinition)
                .document(document)
                .variables(variables)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        when:
        def dfeCopy = newDataFetchingEnvironment(dfe).build()

        then:
        dfe != dfeCopy
        dfe.getContext() == dfeCopy.getContext()
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
    }

}
