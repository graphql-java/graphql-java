package graphql.execution

import graphql.MutationSchema
import graphql.parser.Parser
import spock.lang.Specification

class ExecutionTest extends Specification {

    def parser = new Parser()
    def mutationStrategy = Mock(ExecutionStrategy)
    def queryStrategy = Mock(ExecutionStrategy)
    def execution = new Execution(queryStrategy, mutationStrategy)

    def "query strategy is used for query requests"() {
        given:
        def mutationStrategy = Mock(ExecutionStrategy)

        def queryStrategy = Mock(ExecutionStrategy)
        def execution = new Execution(queryStrategy, mutationStrategy)

        def query = '''
            query {
                numberHolder {
                    theNumber
                }
            }
        '''
        def document = parser.parseDocument(query)

        when:
        execution.execute(MutationSchema.schema, null, document, null, null)

        then:
        1 * queryStrategy.execute(*_)
        0 * mutationStrategy.execute(*_)
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
        execution.execute(MutationSchema.schema, null, document, null, null)

        then:
        0 * queryStrategy.execute(*_)
        1 * mutationStrategy.execute(*_)
    }
}
