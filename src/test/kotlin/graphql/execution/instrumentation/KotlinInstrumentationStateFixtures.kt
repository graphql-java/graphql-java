package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters

class KotlinStatelessInstrumentation : SimplePerformantInstrumentation() {
    val seenStates = mutableListOf<InstrumentationState?>()

    override fun beginExecution(
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult> {
        seenStates.add(state)
        return SimpleInstrumentationContext.noOp()
    }
}

class KotlinChainedInstrumentation(
    instrumentation: Instrumentation,
) : ChainedInstrumentation(instrumentation) {
    fun consumeChildState(state: InstrumentationState?): InstrumentationState? {
        var observedState: InstrumentationState? = null
        chainedConsume(state) { _, childState -> observedState = childState }
        return observedState
    }
}
