package graphql.execution

import graphql.MutationSchema
import graphql.execution.instrumentation.NoOpInstrumentation
import graphql.parser.Parser
import spock.lang.Specification

class ExecutionTest extends Specification {

    def parser = new Parser()
    def subscriptionStrategy = Mock(ExecutionStrategy)
    def mutationStrategy = Mock(ExecutionStrategy)
    def queryStrategy = Mock(ExecutionStrategy)
    def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, NoOpInstrumentation.INSTANCE)

    def "query strategy is used for query requests"() {
        given:
        def mutationStrategy = Mock(ExecutionStrategy)

        def queryStrategy = Mock(ExecutionStrategy)
        def execution = new Execution(queryStrategy, mutationStrategy, subscriptionStrategy, NoOpInstrumentation.INSTANCE)

        def query = '''
            query {
                numberHolder {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(ExecutionId.generate(), MutationSchema.schema, null, null, document, null, null)

        then:
        1 * queryStrategy.execute(*_)
        0 * mutationStrategy.execute(*_)
        0 * subscriptionStrategy.execute(*_)
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
        execution.execute(ExecutionId.generate(), MutationSchema.schema, null, null, document, null, null)

        then:
        0 * queryStrategy.execute(*_)
        1 * mutationStrategy.execute(*_)
        0 * subscriptionStrategy.execute(*_)
    }

    def "subscription strategy is used for subscription requests"() {
        given:
        def query = '''
            subscription {
                numberChanged(newNumber: 1) {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(ExecutionId.generate(), MutationSchema.schema, null, null, document, null, null)

        then:
        0 * queryStrategy.execute(*_)
        0 * mutationStrategy.execute(*_)
        1 * subscriptionStrategy.execute(*_)
    }
}
