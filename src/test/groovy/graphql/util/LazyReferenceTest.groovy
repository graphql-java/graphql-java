package graphql.util

import spock.lang.Specification

class LazyReferenceTest extends Specification {

    def "it can get values"() {
        when:
        LazyReference<String> lazyString = new LazyReference<>({ -> "hi" })

        then:
        lazyString.toString() == "null"

        then:
        lazyString.get() == "hi"
        lazyString.toString() == "hi"
    }

    def "null checks"() {
        when:
        new LazyReference<>(null)
        then:
        thrown(NullPointerException)

        when:
        LazyReference<String> lazyString = new LazyReference<>({ -> null })
        lazyString.get()
        then:
        thrown(NullPointerException)
    }

    @SuppressWarnings(["ChangeToOperator", "GrEqualsBetweenInconvertibleTypes"])
    def ".equals works as expected"() {
        when:
        def lA = new LazyReference({ -> "A" })
        def lA1 = new LazyReference({ -> "A" })
        def lB = new LazyReference({ -> "B" })

        then:
        lA.equals(lA)
        lA.equals(lA1)
        lA.equals("A") // controversial choice but I think it makes sense
        !lA.equals(lB)
        !lA.equals("B")
        !lA.equals(null)
    }

    def "hashCode delegates to value"() {

        when:
        def lA = new LazyReference({ -> "A" })

        then:

        lA.hashCode() == "A".hashCode()

    }
}
