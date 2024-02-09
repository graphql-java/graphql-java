package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.schema.DataFetcher
import graphql.validation.ValidationError

import java.util.concurrent.CompletableFuture

// each implementation gives out a state object with its name
// and then asserts it gets it back with that name

class NamedInstrumentation extends ModernTestingInstrumentation {
    String name


    NamedInstrumentation(String name) {
        instrumentationState = new NamedInstrumentationState(name: name)
        this.name = name
    }

    @Override
    InstrumentationState createState() {
        return instrumentationState
    }

    def assertState(InstrumentationState instrumentationState) {
        assert instrumentationState instanceof NamedInstrumentationState
        assert (instrumentationState as NamedInstrumentationState).name == this.name
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginExecution(parameters, state)
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginParse(parameters, state)
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginValidation(parameters, state)
    }

    @Override
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginExecutionStrategy(parameters, state)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginExecuteOperation(parameters, state)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginField(parameters, state)
    }

    @Override
    InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginFieldExecution(parameters, state)
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.beginFieldFetch(parameters, state)
    }

    @Override
    DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.instrumentDataFetcher(dataFetcher, parameters, state)
    }

    @Override
    CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assertState(state)
        return super.instrumentExecutionResult(executionResult, parameters, state)
    }
}

class NamedInstrumentationState implements InstrumentationState {
    String name
}
