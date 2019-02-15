package graphql.execution.nextgen.result


import graphql.SerializationError
import graphql.execution.ExecutionPath
import graphql.execution.ExecutionStepInfo
import graphql.execution.FetchedValue
import graphql.execution.MergedField
import graphql.execution.nextgen.FetchedValueAnalysis
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR

class ResultNodesUtilTest extends Specification {

    def "convert errors for null values"() {
        given:
        def error = new SerializationError(ExecutionPath.rootPath(), new CoercingSerializeException())
        ExecutionStepInfo executionStepInfo = Mock(ExecutionStepInfo)
        MergedField mergedField = Mock(MergedField)
        mergedField.getResultKey() >> "foo"
        executionStepInfo.getField() >> mergedField
        FetchedValue fetchedValue = FetchedValue.newFetchedValue().fetchedValue(null).build()
        def leafValue = FetchedValueAnalysis.newFetchedValueAnalysis(SCALAR)
                .executionStepInfo(executionStepInfo)
                .nullValue()
                .errors([error])
                .fetchedValue(fetchedValue)
                .build()
        LeafExecutionResultNode leafExecutionResultNode = new LeafExecutionResultNode(leafValue, null)
        ExecutionResultNode executionResultNode = new RootExecutionResultNode([leafExecutionResultNode])

        when:
        def executionResult = ResultNodesUtil.toExecutionResult(executionResultNode)
        then:
        executionResult.errors.size() == 1
    }
}
