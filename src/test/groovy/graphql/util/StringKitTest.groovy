package graphql.util

import spock.lang.Specification

class StringKitTest extends Specification {


    def "can capitalise"() {
        expect:

        def actual = StringKit.capitalize(input)
        actual == expected

        where:
        input | expected
        null  | null
        ""    | ""
        "a"   | "A"
        "abc" | "Abc"

    }
}
