package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification
import spock.lang.Unroll

class ExecutionStrategyExceptionHandlingEquivalenceTest extends Specification {

    class TestInstrumentation extends SimpleInstrumentation {

        @Override
        InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
            throw new AbortExecutionException([new ValidationError(ValidationErrorType.UnknownType)])
        }
    }

    /**
     * a simple set of queries and expected results that each execution strategy should
     * return the same result for, even if they use a different strategy
     */
    @Unroll
    def "#1072 execution strategy exception handling equivalence (strategy: #strategyName)"() {

        def query = """
        {
            hero {
                id
                appearsIn
            }
        }
        """
        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(new TestInstrumentation())
                .queryExecutionStrategy(strategyUnderTest)
                .build()


        expect:

        def executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        result.errors[0] instanceof ValidationError

        where:

        strategyName  | strategyUnderTest
        "async"       | new AsyncExecutionStrategy()
        "asyncSerial" | new AsyncSerialExecutionStrategy()
    }
}
