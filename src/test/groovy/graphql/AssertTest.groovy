package graphql

import spock.lang.Specification

class AssertTest extends Specification {
    def "assertNull should not throw on none null value"() {
        when:
        Assert.assertNotNull("some object")

        then:
        noExceptionThrown()
    }

    def "assertNull should throw on null value"() {
        when:
        Assert.assertNotNull(null)

        then:
        thrown(AssertException)
    }

    def "assertNull with error message should not throw on none null value"() {
        when:
        Assert.assertNotNull("some object", "error message")

        then:
        noExceptionThrown()
    }

    def "assertNull with error message should throw on null value with formatted message"() {
        when:
        Assert.assertNotNull(value, format, arg)

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        value | format     | arg   || expectedMessage
        null  | "error %s" | "msg" || "error msg"
        null  | "code %d"  | 1     || "code 1"
        null  | "code"     | null  || "code"
    }

    def "assertNeverCalled should always throw"() {
        when:
        Assert.assertNeverCalled()

        then:
        def e = thrown(AssertException)
        e.message == "Should never been called"
    }

    def "assertShouldNeverHappen should always throw"() {
        when:
        Assert.assertShouldNeverHappen()

        then:
        def e = thrown(AssertException)
        e.message == "Internal error: should never happen"
    }

    def "assertShouldNeverHappen should always throw with formatted message"() {
        when:
        Assert.assertShouldNeverHappen(format, arg)

        then:
        def error = thrown(AssertException)
        error.message == "Internal error: should never happen: " + expectedMessage

        where:
        format     | arg   || expectedMessage
        "error %s" | "msg" || "error msg"
        "code %d"  | 1     || "code 1"
        "code"     | null  || "code"
    }

    def "assertNotEmpty collection should throw on null or empty"() {
        when:
        Assert.assertNotEmpty(value, format, arg)

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        value | format     | arg   || expectedMessage
        null  | "error %s" | "msg" || "error msg"
        []    | "code %d"  | 1     || "code 1"
    }

    def "assertNotEmpty should not throw on none empty collection"() {
        when:
        Assert.assertNotEmpty(["some object"], "error message")

        then:
        noExceptionThrown()
    }

    def "assertTrue should not throw on true value"() {
        when:
        Assert.assertTrue(true, "error message")

        then:
        noExceptionThrown()
    }

    def "assertTrue with error message should throw on false value with formatted message"() {
        when:
        Assert.assertTrue(false, format, arg)

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        format     | arg   || expectedMessage
        "error %s" | "msg" || "error msg"
        "code %d"  | 1     || "code 1"
        "code"     | null  || "code"
    }

    def "assertValidName should not throw on valid names"() {
        when:
        Assert.assertValidName(name)

        then:
        noExceptionThrown()

        where:
        name     | _
        "msg"    | _
        "__"     | _
        "_01"    | _
        "_a01b1" | _

    }

    def "assertValidName should throw on invalid names"() {
        when:
        Assert.assertValidName(name)

        then:
        def error = thrown(AssertException)
        error.message == "Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]* - was '${name}'"

        where:
        name   | _
        "0abc" | _
        "едц"  | _
        "_()"  | _
    }
}
