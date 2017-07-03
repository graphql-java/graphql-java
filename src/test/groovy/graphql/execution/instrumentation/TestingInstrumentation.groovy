package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.execution.instrumentation.parameters.*
import graphql.language.Document
import graphql.validation.ValidationError

class TestingInstrumentation implements Instrumentation {

    def executionList = []

    @Override
    InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        new TestingInstrumentContext("execution", executionList)
    }

    @Override
    InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new TestingInstrumentContext("parse", executionList)
    }

    @Override
    InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new TestingInstrumentContext("validation", executionList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return new TestingInstrumentContext("data-fetch", executionList)
    }

    @Override
    InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new TestingInstrumentContext("field-$parameters.field.name", executionList)
    }

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new TestingInstrumentContext("fetch-$parameters.field.name", executionList)
    }
}

