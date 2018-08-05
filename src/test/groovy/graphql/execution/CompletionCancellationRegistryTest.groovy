package graphql.execution

import spock.lang.Specification

/**
 * @author jorth
 */
class CompletionCancellationRegistryTest extends Specification {
    def "test callback is invoked immediately"() {
        given:
        def called = 0
        def registry = new CompletionCancellationRegistry()
        registry.dispatch()

        when:
        registry.addCancellationCallback({ called = 1 })

        then:
        called == 1
    }

    def "test parent cancels child"() {
        given:
        def called = 0
        def parentRegistry = new CompletionCancellationRegistry()
        def registry = new CompletionCancellationRegistry(parentRegistry)

        when:
        registry.addCancellationCallback({ called = 1 })

        then:
        called == 0

        when:
        parentRegistry.dispatch()

        then:
        called == 1
    }
}
