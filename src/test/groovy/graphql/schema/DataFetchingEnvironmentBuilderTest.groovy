package graphql.schema


import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

class DataFetchingEnvironmentBuilderTest extends Specification {

    def "Create Environment from existing one will copy everything to new instance"() {
        def dfe = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment()
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
                .dataLoaderRegistry(new DataLoaderRegistry())
                .build()

        when:
        def dfeCopy = DataFetchingEnvironmentBuilder.newDataFetchingEnvironment(dfe).build()

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
        dfe.getDataLoaderRegistry() == dfeCopy.getDataLoaderRegistry()
    }
}
