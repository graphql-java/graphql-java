package graphql.execution.nextgen.result

import graphql.Scalars
import graphql.execution.ExecutionStepInfo

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo

class ExecutionResultNodeTestUtils {


    static ResolvedValue resolvedValue(Object value) {
        return ResolvedValue.newResolvedValue().completedValue(value).build()
    }

    static ExecutionStepInfo esi() {
        return newExecutionStepInfo().type(Scalars.GraphQLString).build()
    }

}
