package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.Document
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.validation.ValidationError

class TestingInstrumentation implements Instrumentation {

    def instrumentationState = new InstrumentationState() {}
    def executionList = []
    List<DataFetchingEnvironment> dfInvocations = []
    List<Class> dfClasses = []

    @Override
    InstrumentationState createState() {
        return instrumentationState
    }

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        new TestingInstrumentContext("execution", executionList)
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("parse", executionList)
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("validation", executionList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("data-fetch", executionList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("field-$parameters.field.name", executionList)
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return new TestingInstrumentContext("fetch-$parameters.field.name", executionList)
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
    ExecutionResult instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        assert parameters.getInstrumentationState() == instrumentationState
        return executionResult
    }
}

