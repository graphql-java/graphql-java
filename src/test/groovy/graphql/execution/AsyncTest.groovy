package graphql.execution

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class AsyncTest extends Specification {

    def "eachSequentially test"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Async.CFFactory)
        def cf1 = new CompletableFuture()
        def cf2 = new CompletableFuture()
        def cf3 = new CompletableFuture()

        when:
        def result = Async.eachSequentially(input, cfFactory)

        then:
        !result.isDone()
        1 * cfFactory.apply('a', 0, []) >> cf1

        when:
        cf1.complete('x')

        then:
        !result.isDone()
        1 * cfFactory.apply('b', 1, ['x']) >> cf2

        when:
        cf2.complete('y')

        then:
        !result.isDone()
        1 * cfFactory.apply('c', 2, ['x', 'y']) >> cf3

        when:
        cf3.complete('z')

        then:
        result.isDone()
        result.get() == ['x', 'y', 'z']
    }

    def "eachSequentially propagates exception"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Async.CFFactory)
        cfFactory.apply('a', 0, _) >> CompletableFuture.completedFuture("x")
        cfFactory.apply('b', 1, _) >> {
            def cf = new CompletableFuture<>()
            cf.completeExceptionally(new RuntimeException("some error"))
            cf
        }

        when:
        def result = Async.eachSequentially(input, cfFactory)

        then:
        result.isCompletedExceptionally()
        Throwable exception
        result.exceptionally({ e ->
            exception = e
        })
        exception instanceof RuntimeException
        exception.message == "some error"
    }

    def "eachSequentially catches factory exception"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Async.CFFactory)
        cfFactory.apply('a', 0, _) >> CompletableFuture.completedFuture("x")
        cfFactory.apply('b', 1, _) >> { throw new RuntimeException("some error") }

        when:
        def result = Async.eachSequentially(input, cfFactory)

        then:
        result.isCompletedExceptionally()
        Throwable exception
        result.exceptionally({ e ->
            exception = e
        })
        exception instanceof RuntimeException
        exception.getMessage() == "some error"
    }

}
