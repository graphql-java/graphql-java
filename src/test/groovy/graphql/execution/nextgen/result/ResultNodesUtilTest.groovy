package graphql.execution.nextgen.result


import graphql.SerializationError
import graphql.execution.ExecutionPath
import graphql.execution.ExecutionStepInfo
import graphql.execution.nextgen.FetchedValue
import graphql.execution.nextgen.FetchedValueAnalysis
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR

class ResultNodesUtilTest extends Specification {

    def "convert errors for null values"() {
        given:
        def error = new SerializationError(ExecutionPath.rootPath(), new CoercingSerializeException())
        ExecutionStepInfo executionStepInfo = Mock(ExecutionStepInfo)
        FetchedValue fetchedValue = new FetchedValue(null, null, [])
        def leafValue = FetchedValueAnalysis.newFetchedValueAnalysis(SCALAR)
                .executionStepInfo(executionStepInfo)
                .nullValue()
                .errors([error])
                .fetchedValue(fetchedValue)
                .build()
        LeafExecutionResultNode leafExecutionResultNode = new LeafExecutionResultNode(leafValue, null)
        ExecutionResultNode executionResultNode = new ObjectExecutionResultNode.RootExecutionResultNode([foo: leafExecutionResultNode])

        when:
        def executionResult = ResultNodesUtil.toExecutionResult(executionResultNode)
        then:
        executionResult.errors.size() == 1
    }
}
