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

class AllNullTestingInstrumentation implements Instrumentation {

    InstrumentationState instrumentationState = new InstrumentationState() {}
    List<String> executionList = []
    List<DataFetchingEnvironment> dfInvocations = []
    List<Class> dfClasses = []
    Map<Object,Object> capturedData = [:]

    @Override
    InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return instrumentationState
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:execution"
        return null
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:parse"
        return null
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:validation"
        return null
    }

    @Override
    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:execution-strategy"
        return null
    }

    @Override
    ExecuteObjectInstrumentationContext beginExecuteObject(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:execute-object"
        return null
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:execute-operation"
        return null
    }

    @Override
    InstrumentationContext<ExecutionResult> beginSubscribedFieldEvent(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:subscribed-field-event-$parameters.field.name"
        return null
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:field-$parameters.field.name"
        return null
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:fetch-$parameters.field.name"
        return null
    }

    @Override
    InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:complete-$parameters.field.name"
        return null
    }

    @Override
    InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters, InstrumentationState state) {
        assert state == instrumentationState
        executionList << "start:complete-list-$parameters.field.name"
        return null
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

