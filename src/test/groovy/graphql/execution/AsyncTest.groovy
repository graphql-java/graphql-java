//file:noinspection GroovyVariableNotAssigned
package graphql.execution

import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.function.BiFunction
import java.util.function.Function

import static java.util.concurrent.CompletableFuture.completedFuture
import static java.util.concurrent.CompletableFuture.runAsync

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

    def "eachSequentially polymorphic test"() {
        given:
        def input = ['a', 'b', 'c', 'd']
        def cfFactory = Mock(BiFunction)
        def cf1 = new CompletableFuture()
        def v2 = 'y'
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
        1 * cfFactory.apply('b', ['x']) >> v2

        when:

        then:
        !result.isDone()
        1 * cfFactory.apply('c', ['x', 'y']) >> cf3

        when:
        cf3.complete(null) // null valued CFS are allowed

        then:
        1 * cfFactory.apply('d', ['x', 'y', null]) >> null // nulls are allowed as values
        result.isDone()
        result.get() == ['x', 'y', null, null]
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

    def "each works for mapping function with polymorphic values"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Function)
        cfFactory.apply('a') >> completedFuture('x')
        cfFactory.apply('b') >> 'y'
        cfFactory.apply('c') >> completedFuture('z')


        when:
        def result = Async.each(input, cfFactory)

        then:
        result.isDone()
        result.get() == ['x', 'y', 'z']
    }

    def "eachPolymorphic works for mapping function with polymorphic values"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Function)
        cfFactory.apply('a') >> completedFuture('x')
        cfFactory.apply('b') >> 'y'
        cfFactory.apply('c') >> completedFuture('z')


        when:
        def result = Async.eachPolymorphic(input, cfFactory)

        then:
        result instanceof CompletableFuture
        (result as CompletableFuture).isDone()
        (result as CompletableFuture).get() == ['x', 'y', 'z']
    }

    def "eachPolymorphic works for mapping function with materialised values"() {
        given:
        def input = ['a', 'b', 'c']
        def cfFactory = Mock(Function)
        cfFactory.apply('a') >> 'x'
        cfFactory.apply('b') >> 'y'
        cfFactory.apply('c') >> 'z'


        when:
        def result = Async.eachPolymorphic(input, cfFactory)

        then:
        result instanceof List
        result == ['x', 'y', 'z']
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

        when: "it has a mix of CFs and objects"
        asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.addObject("0")
        asyncBuilder.addObject("1")
        asyncBuilder.add(completedFuture("2"))
        asyncBuilder.addObject("3")
        asyncBuilder.addObject(completedFuture("4"))

        list = asyncBuilder.await().join()

        then:
        list == ["0", "1", "2", "3", "4"]
    }

    def "can wait on objects of cfs or both with empty or single values"() {
        when:
        def asyncBuilder = Async.ofExpectedSize(0)
        def list = asyncBuilder.await().join()

        then:
        list == []

        when:
        asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.add(completedFuture("A"))
        list = asyncBuilder.await().join()

        then:
        list == ["A"]

        when:
        asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject(completedFuture("A"))
        list = asyncBuilder.await().join()

        then:
        list == ["A"]

        when:
        asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject("A")
        list = asyncBuilder.await().join()

        then:
        list == ["A"]
    }

    def "await polymorphic works as expected"() {

        when:
        def asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.add(completedFuture("0"))
        asyncBuilder.add(completedFuture("1"))
        asyncBuilder.addObject("2")
        asyncBuilder.addObject("3")
        asyncBuilder.add(completedFuture("4"))

        def awaited = asyncBuilder.awaitPolymorphic()

        then:
        awaited instanceof CompletableFuture
        joinOrMaterialized(awaited) == ["0", "1", "2", "3", "4"]

        when:
        asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.addObject(completedFuture("0"))
        asyncBuilder.addObject(completedFuture("1"))
        asyncBuilder.addObject(completedFuture("2"))
        asyncBuilder.addObject(completedFuture("3"))
        asyncBuilder.addObject(completedFuture("4"))

        awaited = asyncBuilder.awaitPolymorphic()

        then:
        awaited instanceof CompletableFuture
        joinOrMaterialized(awaited) == ["0", "1", "2", "3", "4"]

        when:
        asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.addObject("0")
        asyncBuilder.addObject("1")
        asyncBuilder.addObject("2")
        asyncBuilder.addObject("3")
        asyncBuilder.addObject("4")

        awaited = asyncBuilder.awaitPolymorphic()

        then:
        !(awaited instanceof CompletableFuture)
        joinOrMaterialized(awaited) == ["0", "1", "2", "3", "4"]

        when:
        asyncBuilder = Async.ofExpectedSize(0)

        awaited = asyncBuilder.awaitPolymorphic()

        then:
        !(awaited instanceof CompletableFuture)
        joinOrMaterialized(awaited) == []

        when:
        asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject("A")

        awaited = asyncBuilder.awaitPolymorphic()

        then:
        !(awaited instanceof CompletableFuture)
        joinOrMaterialized(awaited) == ["A"]

        when:
        asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject(completedFuture("A"))

        awaited = asyncBuilder.awaitPolymorphic()

        then:
        awaited instanceof CompletableFuture
        joinOrMaterialized(awaited) == ["A"]
    }

    def "await polymorphic works as expected with nulls"() {

        when:
        def asyncBuilder = Async.ofExpectedSize(5)
        asyncBuilder.add(completedFuture("0"))
        asyncBuilder.add(completedFuture(null))
        asyncBuilder.addObject("2")
        asyncBuilder.addObject(null)
        asyncBuilder.add(completedFuture("4"))

        def awaited = asyncBuilder.awaitPolymorphic()

        then:
        awaited instanceof CompletableFuture
        joinOrMaterialized(awaited) == ["0", null, "2", null, "4"]
    }

    def "toCompletableFutureOrMaterializedObject tested"() {
        def x = "x"
        def cf = completedFuture(x)

        when:
        def object = Async.toCompletableFutureOrMaterializedObject(x)
        then:
        object == x

        when:
        object = Async.toCompletableFutureOrMaterializedObject(cf)
        then:
        object == cf
    }

    Object joinOrMaterialized(Object awaited) {
        if (awaited instanceof CompletableFuture) {
            return ((CompletableFuture) awaited).join()
        } else {
            return awaited
        }
    }

    def "await with null cancelCF behaves like plain await"() {
        when:
        def asyncBuilder = Async.ofExpectedSize(3)
        asyncBuilder.add(completedFuture("A"))
        asyncBuilder.add(completedFuture("B"))
        asyncBuilder.add(completedFuture("C"))
        def list = asyncBuilder.await((CompletableFuture) null).join()

        then:
        list == ["A", "B", "C"]
    }

    def "await with cancelCF returns all results when all CFs complete before cancellation"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(3)
        asyncBuilder.add(completedFuture("A"))
        asyncBuilder.add(completedFuture("B"))
        asyncBuilder.add(completedFuture("C"))
        def list = asyncBuilder.await(cancelCF).join()

        then:
        list == ["A", "B", "C"]
    }

    def "await with cancelCF returns partial results when cancellation fires before all CFs complete"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def pending1 = new CompletableFuture<String>()
        def pending2 = new CompletableFuture<String>()

        def asyncBuilder = Async.ofExpectedSize(4)
        asyncBuilder.add(completedFuture("A"))
        asyncBuilder.add(pending1)
        asyncBuilder.add(completedFuture("C"))
        asyncBuilder.add(pending2)

        def resultCF = asyncBuilder.await(cancelCF)

        // cancel before pending CFs complete
        cancelCF.complete(null)

        def list = resultCF.join()

        then:
        list == ["A", null, "C", null]
    }

    def "await with cancelCF returns partial results with mixed objects and CFs"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def pending = new CompletableFuture<String>()

        def asyncBuilder = Async.ofExpectedSize(4)
        asyncBuilder.addObject("A")
        asyncBuilder.add(completedFuture("B"))
        asyncBuilder.add(pending)
        asyncBuilder.addObject("D")

        def resultCF = asyncBuilder.await(cancelCF)

        cancelCF.complete(null)
        def list = resultCF.join()

        then:
        list == ["A", "B", null, "D"]
    }

    def "await with cancelCF returns full results when all CFs complete even if cancelCF completes later"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def cf1 = new CompletableFuture<String>()
        def cf2 = new CompletableFuture<String>()

        def asyncBuilder = Async.ofExpectedSize(2)
        asyncBuilder.add(cf1)
        asyncBuilder.add(cf2)

        def resultCF = asyncBuilder.await(cancelCF)

        // complete all CFs before cancellation
        cf1.complete("X")
        cf2.complete("Y")

        def list = resultCF.join()

        then: "full results returned despite cancel firing after"
        list == ["X", "Y"]

        when: "cancelCF completes after all CFs - should not affect result"
        cancelCF.complete(null)

        then: "result is unchanged"
        resultCF.join() == ["X", "Y"]
    }

    def "await with cancelCF propagates exception if a CF fails before cancellation"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def failing = new CompletableFuture<String>()
        failing.completeExceptionally(new RuntimeException("boom"))

        def asyncBuilder = Async.ofExpectedSize(2)
        asyncBuilder.add(completedFuture("A"))
        asyncBuilder.add(failing)

        def resultCF = asyncBuilder.await(cancelCF)
        resultCF.join()

        then:
        thrown(CompletionException)
    }

    def "await with cancelCF works with all materialised values"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(3)
        asyncBuilder.addObject("A")
        asyncBuilder.addObject("B")
        asyncBuilder.addObject("C")
        // make cancel happen soon but off thread
        runAsync({ -> cancelCF.complete(null) })
        def list = asyncBuilder.await(cancelCF).join()

        then:
        list == ["A", "B", "C"]
    }

    def "await with null cancelCF delegates to plain await"() {
        when: "a many builder is awaited with a null cancellation future"
        def asyncBuilder = Async.ofExpectedSize(2)
        asyncBuilder.add(completedFuture("A"))
        asyncBuilder.add(completedFuture("B"))
        def list = asyncBuilder.await((CompletableFuture<Void>) null).join()

        then: "it behaves identically to await() and returns all results"
        list == ["A", "B"]
    }

    def "await with cancelCF on empty builder returns empty list"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(0)
        def list = asyncBuilder.await(cancelCF).join()

        then:
        list == []
    }

    def "await with cancelCF on single builder can return completed values"() {
        when: "single builder with a completed CF"
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.add(completedFuture("A"))
        // make cancel happen soon but off thread
        runAsync({ -> cancelCF.complete(null) })
        def list = asyncBuilder.await(cancelCF).join()

        then: "result is returned normally"
        list == ["A"]
    }

    def "await with cancelCF on single builder can return exceptions"() {
        when: "single builder with a completed CF"
        def cancelCF = new CompletableFuture<Void>()
        def failing = new CompletableFuture<String>()
        failing.completeExceptionally(new RuntimeException("boom"))

        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.add(failing)

        // make cancel happen soon but off thread
        runAsync({ -> cancelCF.complete(null) })
        def list = asyncBuilder.await(cancelCF).join()

        then: "result is exceptional"
        thrown(CompletionException)
    }

    def "await with null cancelCF on single builder will return completed values"() {
        when: "single builder with a completed CF"
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.add(completedFuture("A"))
        def list = asyncBuilder.await(null).join()

        then: "result is returned normally"
        list == ["A"]
    }

    def "await with null cancelCF on single builder will return materialised value"() {
        when: "single builder with a completed CF"
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject("A")
        def list = asyncBuilder.await(null).join()

        then: "result is returned normally"
        list == ["A"]
    }

    def "await with cancelCF on single builder can be cancelled"() {
        when: "single builder with a completed CF"
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.add(new CompletableFuture<Object>())

        // make cancel happen soon but off thread
        runAsync({ -> cancelCF.complete(null) })

        def list = asyncBuilder.await(cancelCF).join()

        then: "the single value is null since it never completed"
        list == [null]
    }

    def "await with cancelCF on single builder with materialised value returns it"() {
        when:
        def cancelCF = new CompletableFuture<Void>()
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject("A")

        // make cancel happen soon but off thread
        runAsync({ -> cancelCF.complete(null) })

        def list = asyncBuilder.await(cancelCF).join()

        then:
        list == ["A"]
    }

    def "await with null cancelCF on single builder with materialised value returns it"() {
        when:
        def asyncBuilder = Async.ofExpectedSize(1)
        asyncBuilder.addObject("A")

        def list = asyncBuilder.await(null).join()

        then:
        list == ["A"]
    }
}
