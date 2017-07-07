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

    def executionList = []
    List<DataFetchingEnvironment> dfInvocations = []
    List<Class> dfClasses = []

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

    @Override
    DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher) {
        dfClasses.add(dataFetcher.getClass())
        return new DataFetcher<Object>() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                dfInvocations.add(environment)
                dataFetcher.get(environment)
            }
        }
    }
}

