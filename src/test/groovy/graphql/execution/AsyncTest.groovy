package graphql.execution

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.function.BiFunction
import java.util.function.Function

import static java.util.concurrent.CompletableFuture.completedFuture

class AsyncTest extends Specification {

    def "eachSequentially test"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(BiFunction)
        def cf1 = new CompletableFuture()
        def cf2 = new CompletableFuture()
        def cf3 = new CompletableFuture()

        when:
        def result = Async.eachSequentially(input, cfFactory)

        then:
        !result.isDone()
        1 * cfFactory.apply('a', []) >> cf1

        when:
        cf1.complete('x')

        then:
        !result.isDone()
        1 * cfFactory.apply('b', ['x']) >> cf2

        when:
        cf2.complete('y')

        then:
        !result.isDone()
        1 * cfFactory.apply('c', ['x', 'y']) >> cf3

        when:
        cf3.complete('z')

        then:
        result.isDone()
        result.get() == ['x', 'y', 'z']
    }

    def "eachSequentially propagates exception"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(BiFunction)
        cfFactory.apply('a', _) >> completedFuture("x")
        cfFactory.apply('b', _) >> {
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
        def cfFactory = Mock(BiFunction)
        cfFactory.apply('a', _) >> completedFuture("x")
        cfFactory.apply('b', _) >> { throw new RuntimeException("some error") }

        when:
        def result = Async.eachSequentially(input, cfFactory)

        then:
        result.isCompletedExceptionally()
        Throwable exception
        result.exceptionally({ e ->
            exception = e
        })
        exception instanceof CompletionException
        exception.getCause().getMessage() == "some error"
    }

    def "each works for mapping function"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Function)
        cfFactory.apply('a') >> completedFuture('x')
        cfFactory.apply('b') >> completedFuture('y')
        cfFactory.apply('c') >> completedFuture('z')


        when:
        def result = Async.each(input, cfFactory)

        then:
        result.isDone()
        result.get() == ['x', 'y', 'z']
    }

    def "each with mapping function propagates factory exception"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Function)

        when:
        def result = Async.each(input, cfFactory)

        then:
        1 * cfFactory.apply('a') >> completedFuture('x')
        1 * cfFactory.apply('b') >> { throw new RuntimeException('some error') }
        1 * cfFactory.apply('c') >> completedFuture('z')
        result.isCompletedExceptionally()
        Throwable exception
        result.exceptionally({ e ->
            exception = e
        })
        exception instanceof CompletionException
        exception.getCause().getMessage() == "some error"
    }

    def "can wait on objects of cfs or both"() {
        when:
        def asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.add(completedFuture("0"))
        asyncBuilder.add(completedFuture("1"))
        asyncBuilder.addObject("2")
        asyncBuilder.addObject("3")
        asyncBuilder.add(completedFuture("4"))

        def list = asyncBuilder.await().join()

        then:
        list == ["0", "1", "2", "3", "4"]

        when:
        asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.add(completedFuture("0"))
        asyncBuilder.add(completedFuture("1"))
        asyncBuilder.add(completedFuture("2"))
        asyncBuilder.add(completedFuture("3"))
        asyncBuilder.add(completedFuture("4"))

        list = asyncBuilder.await().join()

        then:
        list == ["0", "1", "2", "3", "4"]

        when:
        asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.addObject("0")
        asyncBuilder.addObject("1")
        asyncBuilder.addObject("2")
        asyncBuilder.addObject("3")
        asyncBuilder.addObject("4")

        list = asyncBuilder.await().join()

        then:
        list == ["0", "1", "2", "3", "4"]
    }
}
