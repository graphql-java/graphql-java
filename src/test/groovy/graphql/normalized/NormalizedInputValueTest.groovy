package graphql.normalized

import graphql.AssertException
import spock.lang.Specification
import spock.lang.Unroll

class NormalizedInputValueTest extends Specification {

    @Unroll
    def "can get unwrapped type name - #typename"() {
        expect:

        def value = new NormalizedInputValue(typename, 1)
        value.getTypeName() == typename
        value.getUnwrappedTypeName() == unwrappedTypeName

        where:
        typename       | unwrappedTypeName
        "[TypeName!]!" | "TypeName"
        "[TypeName!]"  | "TypeName"
        "[TypeName]"   | "TypeName"
        "TypeName!"    | "TypeName"
        "TypeName!"    | "TypeName"
        "TypeName"     | "TypeName"
    }

    @Unroll
    def "is list like- #typename"() {
        expect:

        def value = new NormalizedInputValue(typename, 1)
        value.isListLike() == expected

        where:
        typename       | expected
        "[TypeName!]!" | true
        "[TypeName!]"  | true
        "[TypeName]"   | true
        "TypeName!"    | false
        "TypeName"     | false
    }

    @Unroll
    def "is nullable - #typename"() {
        expect:

        def value = new NormalizedInputValue(typename, 1)
        value.isNonNullable() == expectedNonNullable
        value.isNullable() == !expectedNonNullable

        where:
        typename       | expectedNonNullable
        "[TypeName!]!" | true
        "[TypeName!]"  | false
        "[TypeName]"   | false
        "TypeName!"    | true
        "TypeName"     | false
    }

    @Unroll
    def "bad input will be rejected = #typename"() {
        expect:

        try {
            new NormalizedInputValue(typename, 1)
            assert valid, "This should have thrown an assert"
        } catch (AssertException ignored) {
            assert !valid, "This should have been valid"
        }

        where:
        typename    | valid
        "   !"      | false
        ""          | false
        "   "       | false
        " Name"     | false
        "Name  "    | false
        "[]!"       | false
        "!!"        | false
        "[Name"     | false
        "Name]"     | false
        null        | false
        "[Valid]"   | true
        "[Valid]!"  | true
        "[Valid!]!" | true
    }
}
