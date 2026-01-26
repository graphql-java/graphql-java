package graphql.execution

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.TestUtil
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.language.SourceLocation
import graphql.validation.ValidationError
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class AbortExecutionExceptionTest extends Specification {

    class BasicError implements GraphQLError {
        def message

        @Override
        String getMessage() {
            return message
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }
    }

    def "to execution result handling"() {
        AbortExecutionException e
        when:
        e = new AbortExecutionException("No underlying errors")
        then:
        e.toExecutionResult().getErrors().size() == 1
        e.toExecutionResult().getErrors()[0].message == "No underlying errors"

        when:
        e = new AbortExecutionException([new BasicError(message: "UnderlyingA"), new BasicError(message: "UnderlyingB")])
        then:
        e.toExecutionResult().getErrors().size() == 2
        e.toExecutionResult().getErrors()[0].message == "UnderlyingA"
        e.toExecutionResult().getErrors()[1].message == "UnderlyingB"
    }

    def "will call instrumentation.instrumentExecutionResult() at the end"() {
        def sdl = """
            type Query {
                q : Q
            }
            
            type Q {
                name : String
            }
        """


        def schema = TestUtil.schema(sdl)

        def throwOnEarlyPhase = true
        Instrumentation instrumentation = new SimplePerformantInstrumentation() {
            @Override
            InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters, InstrumentationState state) {
                if (throwOnEarlyPhase) {
                    throw new AbortExecutionException("early")
                }
                return super.beginValidation(parameters, state)
            }

            @Override
            InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
                if (!throwOnEarlyPhase) {
                    throw new AbortExecutionException("later")
                }
                return super.beginExecuteOperation(parameters, state)
            }

            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                def newER = executionResult.transform { it.extensions([extra: "extensions"]) }
                return CompletableFuture.completedFuture(newER)
            }
        }
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()


        def executionInput = ExecutionInput.newExecutionInput("query q { q {name}}")
                .root([q: [name: "nameV"]])
                .build()

        when:
        def er = graphQL.execute(executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0].message == "early"
        er.extensions == [extra: "extensions"]

        when:
        throwOnEarlyPhase = false
        er = graphQL.execute(executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0].message == "later"
        er.extensions == [extra: "extensions"]
    }
}
