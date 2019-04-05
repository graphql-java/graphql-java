package graphql.execution.nextgen.result

import graphql.Scalars
import graphql.execution.nextgen.FetchedValueAnalysis

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.FetchedValue.newFetchedValue
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR
import static graphql.execution.nextgen.FetchedValueAnalysis.newFetchedValueAnalysis

class ExecutionResultNodeTestUtils {

    static FetchedValueAnalysis fvaForValue(Object value) {
        def executionStepInfo = newExecutionStepInfo().type(Scalars.GraphQLString).build()
        def fetchedValue = newFetchedValue()
                .fetchedValue(value)
                .rawFetchedValue(value).build()
        newFetchedValueAnalysis()
                .executionStepInfo(executionStepInfo)
                .valueType(SCALAR)
                .fetchedValue(fetchedValue)
                .completedValue(value)
                .build()
    }

}
