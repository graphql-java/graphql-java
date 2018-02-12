package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionContextParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionResultParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLSchema
import graphql.validation.ValidationError

import java.util.concurrent.CompletableFuture

class TestingInstrumentation implements Instrumentation {

    def preExecutionState = new InstrumentationPreExecutionState() {}
    def instrumentationState = new InstrumentationState() {}
    def executionList = []
    List<Throwable> throwableList = []
    List<DataFetchingEnvironment> dfInvocations = []
    List<Class> dfClasses = []

    @Override
    InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
        return preExecutionState
    }

    @Override
    InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return instrumentationState
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        new TestingInstrumentContext("execution", executionList, throwableList)
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        return new TestingInstrumentContext("parse", executionList, throwableList)
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        return new TestingInstrumentContext("validation", executionList, throwableList)
    }

    @Override
    InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("execution-strategy", executionList, throwableList)
    }

    @Override
    InstrumentationContext<CompletableFuture<ExecutionResult>> beginDataFetchDispatch(InstrumentationDataFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("data-fetch-dispatch", executionList, throwableList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("data-fetch", executionList, throwableList)
    }

    @Override
    InstrumentationContext<Map<String, List<Field>>> beginFields(InstrumentationExecutionStrategyParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("fields", executionList, throwableList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("field-$parameters.field.name", executionList, throwableList)
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("fetch-$parameters.field.name", executionList, throwableList)
    }

    @Override
    InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteField(InstrumentationFieldCompleteParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("complete-$parameters.field.name", executionList, throwableList)
    }

    @Override
    InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("complete-list-$parameters.field.name", executionList, throwableList)
    }

    @Override
    GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        return schema
    }

    @Override
    ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        return executionInput
    }

    @Override
    ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionContextParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return executionContext
    }

    @Override
    DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        dfClasses.add(dataFetcher.getClass())
        return new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                dfInvocations.add(environment)
                dataFetcher.get(environment)
            }
        }
    }

    @Override
    CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionResultParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return CompletableFuture.completedFuture(executionResult)
    }

    @Override
    CompletableFuture<ExecutionResult> instrumentFinalExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        assert parameters.getPreExecutionState() == preExecutionState
        return CompletableFuture.completedFuture(executionResult)
    }
}

