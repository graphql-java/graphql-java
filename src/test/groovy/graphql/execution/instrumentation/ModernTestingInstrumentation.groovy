package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLSchema
import graphql.validation.ValidationError

import java.util.concurrent.CompletableFuture

/**
 * This class overrides the new methods that take state directly as a parameter
 */
class ModernTestingInstrumentation implements Instrumentation {

    InstrumentationState instrumentationState = new InstrumentationState() {}
    List<String> executionList = []
    List<Throwable> throwableList = []
    List<DataFetchingEnvironment> dfInvocations = []
    List<Class> dfClasses = []
    Map<Object,Object> capturedData = [:]
    boolean useOnDispatch = false

    @Override
    InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return instrumentationState
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        new TestingInstrumentContext("execution", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("parse", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("validation", executionList, throwableList, useOnDispatch)
    }

    @Override
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingExecutionStrategyInstrumentationContext("execution-strategy", executionList, throwableList, useOnDispatch)
    }

    @Override
    ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingExecuteObjectInstrumentationContext("execute-object", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("execute-operation", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("subscribed-field-event-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("field-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("field-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("fetch-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("complete-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<Object> beginFieldCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("complete-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("complete-list-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    InstrumentationContext<Object> beginFieldListCompletion(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return new TestingInstrumentContext("complete-list-$parameters.field.name", executionList, throwableList, useOnDispatch)
    }

    @Override
    ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return executionInput
    }

    @Override
    DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return documentAndVariables
    }

    @Override
    GraphQLSchema instrumentSchema(GraphQLSchema schema, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return schema
    }

    @Override
    ExecutionContext instrumentExecutionContext(ExecutionContext executionContext, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return executionContext
    }

    @Override
    DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
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
    CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        return CompletableFuture.completedFuture(executionResult)
    }
}

