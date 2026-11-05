package graphql.execution

import graphql.GraphqlErrorBuilder
import spock.lang.Specification
import spock.lang.Timeout

class ExecutionContextTest extends Specification {

    @Timeout(10)
    def "adding field errors does not copy the error list on every add"() {
        // aliased fields over a list can produce one error per field per element. each add used to
        // copy the whole error list, so the cost grew with the square of the number of errors
        given:
        def executionContext = new ExecutionContextBuilder().executionId(ExecutionId.generate()).build()
        def error = GraphqlErrorBuilder.newError().message("bad").build()
        def count = 200_000

        when:
        for (int i = 0; i < count; i++) {
            executionContext.addError(error)
        }
        def errors = executionContext.getErrors()
        executionContext.addError(error, ResultPath.rootPath().segment("late"))

        then:
        errors.size() == count
        executionContext.getErrors().size() == count + 1
    }
}
