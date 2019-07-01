package graphql.execution.nextgen.result

import graphql.SerializationError
import graphql.execution.ExecutionPath
import graphql.execution.ExecutionStepInfo
import graphql.execution.MergedField
import graphql.schema.CoercingSerializeException
import spock.lang.Specification

class ResultNodesUtilTest extends Specification {

    def "convert errors for null values"() {
        given:
        def error = new SerializationError(ExecutionPath.rootPath(), new CoercingSerializeException())
        ExecutionStepInfo executionStepInfo = Mock(ExecutionStepInfo)
        MergedField mergedField = Mock(MergedField)
        mergedField.getResultKey() >> "foo"
        executionStepInfo.getField() >> mergedField
        ResolvedValue resolvedValue = ResolvedValue.newResolvedValue()
                .completedValue(null)
                .nullValue(true)
                .errors([error])
                .build()

        LeafExecutionResultNode leafExecutionResultNode = new LeafExecutionResultNode(executionStepInfo, resolvedValue, null)
        ExecutionResultNode executionResultNode = new RootExecutionResultNode([leafExecutionResultNode])

        when:
        def executionResult = ResultNodesUtil.toExecutionResult(executionResultNode)
        then:
        executionResult.errors.size() == 1
    }
}
