package graphql.execution

import graphql.EngineRunningState
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.MutationSchema
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.normalized.nf.provider.NoOpNormalizedDocumentProvider
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
    def normalizedDocumentProvider = NoOpNormalizedDocumentProvider.INSTANCE;
    def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, SimplePerformantInstrumentation.INSTANCE, ValueUnboxer.DEFAULT, false, normalizedDocumentProvider)
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
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState, new EngineRunningState(emptyExecutionInput))

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
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState, new EngineRunningState(emptyExecutionInput))

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
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState, new EngineRunningState(emptyExecutionInput))

        then:
        queryStrategy.execute == 0
        mutationStrategy.execute == 0
        subscriptionStrategy.execute == 1
    }

    def "Update query strategy when instrumenting execution context"() {
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

        def instrumentation = new SimplePerformantInstrumentation() {

            @Override
            ExecutionContext instrumentExecutionContext(ExecutionContext executionContext,
                                                        InstrumentationExecutionParameters parameters,
                                                        InstrumentationState state) {

                return ExecutionContextBuilder.newExecutionContextBuilder(executionContext)
                        .queryStrategy(queryStrategyUpdatedToDuringExecutionContextInstrument)
                        .build()
            }
        }

        def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, instrumentation, ValueUnboxer.DEFAULT, false, NoOpNormalizedDocumentProvider.INSTANCE)


        when:
        execution.execute(document, MutationSchema.schema, ExecutionId.generate(), emptyExecutionInput, instrumentationState, new EngineRunningState(emptyExecutionInput))

        then:
        queryStrategy.execute == 0
        mutationStrategy.execute == 0
        subscriptionStrategy.execute == 0
        queryStrategyUpdatedToDuringExecutionContextInstrument.execute == 1
    }


}
