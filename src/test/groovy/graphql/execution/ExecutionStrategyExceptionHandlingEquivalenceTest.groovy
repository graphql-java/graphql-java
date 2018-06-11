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

/**
 * This allows the testing of different execution strategies that handle exceptions during data fetching in the same way
 */
class ExecutionStrategyExceptionHandlingEquivalenceTest extends Specification {

  class TestInstrumentation extends SimpleInstrumentation {

    @Override
    InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
      throw new AbortExecutionException(Arrays.asList(new ValidationError(ValidationErrorType.UnknownType)))
    }
  }

  /**
   * @return a simple set of queries and expected results that each execution strategy should
   * return the same result for, even if they use a different strategy
   */
  @Unroll
  def "execution strategy exception handling equivalence ()"() {

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

    assert result.errors[0].message.equals(
      "Validation error of type UnknownType: null"): "${strategyType} did not pass through the underlying error from AbortExecutionException"

    where:

    strategyType  | strategyUnderTest
    // "async"      | new AsyncExecutionStrategy()
    "asyncSerial" | new AsyncSerialExecutionStrategy()
  }
}

