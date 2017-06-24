package graphql.execution

import graphql.ErrorType
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.language.SourceLocation
import graphql.schema.GraphQLFieldDefinition
import spock.lang.Specification

class ExecutionStrategyExceptionHandlerTest extends Specification {

    def "If no handler is injected, the legacy inheritance-based handler is used"() {
        given:
        ExecutionStrategy executionStrategyWithLegacyExceptionHandler = new ExceptionHandlingExecutionStrategy()
        ExecutionContext executionContext = new ExecutionContext(null, null, null, executionStrategyWithLegacyExceptionHandler, executionStrategyWithLegacyExceptionHandler, executionStrategyWithLegacyExceptionHandler, null, null, null, null, null)

        when:
        executionStrategyWithLegacyExceptionHandler.execute(executionContext, null)

        then:
        executionContext.getErrors().size() == 1
        executionContext.getErrors().get(0) instanceof ErrorCreatedByLegacyHandler
    }

    def "Injected custom exception handler is used handle exceptions"() {
        given:
        ExecutionExceptionHandler injectableExceptionHandler = new TestExecutionExceptionHandler()
        ExecutionStrategy executionStrategyWithInjectedExceptionHandler = new ExceptionHandlingExecutionStrategy(injectableExceptionHandler)
        ExecutionContext executionContext = new ExecutionContext(null, null, null, executionStrategyWithInjectedExceptionHandler, executionStrategyWithInjectedExceptionHandler, executionStrategyWithInjectedExceptionHandler, null, null, null, null, null)

        when:
        executionStrategyWithInjectedExceptionHandler.execute(executionContext, null)

        then:
        executionContext.getErrors().size() == 1
        executionContext.getErrors().get(0) instanceof ErrorCreatedByInjectedHandler
    }


    class ExceptionHandlingExecutionStrategy extends ExecutionStrategy {

        ExceptionHandlingExecutionStrategy() {
        }

        ExceptionHandlingExecutionStrategy(ExecutionExceptionHandler exceptionHandler) {
            super(exceptionHandler)
        }

        @Override
        ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
            this.exceptionHandler.handleDataFetchingException(executionContext, null, null, null, null)
            return null
        }

        @Override
        protected void handleDataFetchingException(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, Map<String, Object> argumentValues, ExecutionPath path, Exception e) {
            executionContext.addError(new ErrorCreatedByLegacyHandler())
        }
    }

    class TestExecutionExceptionHandler implements ExecutionExceptionHandler {

        @Override
        void handleDataFetchingException(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, Map<String, Object> argumentValues, ExecutionPath path, Exception e) {
            executionContext.addError(new ErrorCreatedByInjectedHandler())
        }
    }

    class ErrorCreatedByInjectedHandler implements GraphQLError {

        @Override
        String getMessage() {
            return null
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return null
        }
    }

    class ErrorCreatedByLegacyHandler implements GraphQLError {

        @Override
        String getMessage() {
            return null
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return null
        }
    }


}
