package graphql.execution

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.MutationSchema
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.parser.Parser
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static java.util.Collections.emptyList

class ExecutionTest extends Specification {

    class CountingExecutionStrategy extends ExecutionStrategy {
        int execute = 0


        @Override
        CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
            execute++
            return CompletableFuture.completedFuture(result())
        }

        private ExecutionResultImpl result() {
            new ExecutionResultImpl(emptyList())
        }
    }

    def parser = new Parser()
    def subscriptionStrategy = new CountingExecutionStrategy()
    def mutationStrategy = new CountingExecutionStrategy()
    def queryStrategy = new CountingExecutionStrategy()
    def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, SimpleInstrumentation.INSTANCE, PossibleOptionalUnboxer.DEFAULT)
    def emptyExecutionInput = ExecutionInput.newExecutionInput().query("query").build()
    def instrumentationState = new InstrumentationState() {}

    def "query strategy is used for query requests"() {
        given:
        def query = '''
            query {
                numberHolder {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState)

        then:
        queryStrategy.execute == 1
        mutationStrategy.execute == 0
        subscriptionStrategy.execute == 0
    }

    def "mutation strategy is used for mutation requests"() {
        given:
        def query = '''
            mutation {
                changeTheNumber(newNumber: 1) {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState)

        then:
        queryStrategy.execute == 0
        mutationStrategy.execute == 1
        subscriptionStrategy.execute == 0
    }

    def "subscription strategy is used for subscription requests"() {
        given:
        def query = '''
            subscription {
                changeNumberSubscribe(clientId: 1) {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState)

        then:
        queryStrategy.execute == 0
        mutationStrategy.execute == 0
        subscriptionStrategy.execute == 1
    }
	
	def "Update query strategy when instrumenting exection context" (){
		given:
		def query = '''
            query {
                numberHolder {
                    theNumber
                }
            }
        '''
		def document = parser.parseDocument(query)
		def queryStrategyUpdatedToDuringExecutionContextInstrument = new CountingExecutionStrategy()
		
		def instrumentation = new SimpleInstrumentation() {

			@Override
			public ExecutionContext instrumentExecutionContext(ExecutionContext executionContext,
					InstrumentationExecutionParameters parameters) {
					
					return ExecutionContextBuilder.newExecutionContextBuilder(executionContext)
					.queryStrategy(queryStrategyUpdatedToDuringExecutionContextInstrument)
					.build();
			}
		}

        def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation, PossibleOptionalUnboxer.DEFAULT)
		
		
		when:
		execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState)

		then:
		queryStrategy.execute == 0
		mutationStrategy.execute == 0
		subscriptionStrategy.execute == 0
		queryStrategyUpdatedToDuringExecutionContextInstrument.execute == 1
	}
	
	
}
