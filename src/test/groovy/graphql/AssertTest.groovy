package graphql

import spock.lang.Specification

import static graphql.Assert.*

class AssertTest extends Specification {
    def "assertNotNull should not throw on none null value"() {
        when:
        assertNotNull("some object")

        then:
        noExceptionThrown()
    }

    def "assertNotNull should throw on null value"() {
        when:
        assertNotNull(null)

        then:
        thrown(AssertException)
    }

    def "assertNotNull constant message should throw on null value"() {
        when:
        assertNotNull(null, "constant message")

        then:
        def error = thrown(AssertException)
        error.message == "constant message"
    }

    def "assertNotNull with error message should not throw on none null value"() {
        when:
        assertNotNull("some object", { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertNotNull with error message should throw on null value with formatted message"() {
        when:
        assertNotNull(value, { -> String.format(format, arg) })

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        value | format     | arg   || expectedMessage
        null  | "error %s" | "msg" || "error msg"
        null  | "code %d"  | 1     || "code 1"
        null  | "code"     | null  || "code"
    }

    def "assertNotNull with different number of  error args throws assertions"() {
        when:
        toRun.run()

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        toRun                                                                              | expectedMessage
        runnable({ assertNotNull(null, "error %s", "arg1") })                       | "error arg1"
        runnable({ assertNotNull(null, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertNotNull(null, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertNotNull with different number of error args with non null does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                             | expectedMessage
        runnable({ assertNotNull("x", "error %s", "arg1") })                       | "error arg1"
        runnable({ assertNotNull("x", "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertNotNull("x", "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertNeverCalled should always throw"() {
        when:
        assertNeverCalled()

        then:
        def e = thrown(AssertException)
        e.message == "Should never been called"
    }

    def "assertShouldNeverHappen should always throw"() {
        when:
        assertShouldNeverHappen()

        then:
        def e = thrown(AssertException)
        e.message == "Internal error: should never happen"
    }

    def "assertShouldNeverHappen should always throw with formatted message"() {
        when:
        assertShouldNeverHappen(format, arg)

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
        assertNotEmpty(value, { -> String.format(format, arg) })

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
        assertNotEmpty(["some object"], { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertTrue should not throw on true value"() {
        when:
        assertTrue(true, { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertTrue with error message should throw on false value with formatted message"() {
        when:
        assertTrue(false, { -> String.format(format, arg) })

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        format     | arg   || expectedMessage
        "error %s" | "msg" || "error msg"
        "code %d"  | 1     || "code 1"
        "code"     | null  || "code"
    }

    def "assertTrue constant message should throw with message"() {
        when:
        assertTrue(false, "constant message")

        then:
        def error = thrown(AssertException)
        error.message == "constant message"
    }

    def "assertTrue with different number of error args throws assertions"() {
        when:
        toRun.run()

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        toRun                                                                            | expectedMessage
        runnable({ assertTrue(false, "error %s", "arg1") })                       | "error arg1"
        runnable({ assertTrue(false, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertTrue(false, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertTrue with different number of error args but false does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                           | expectedMessage
        runnable({ assertTrue(true, "error %s", "arg1") })                       | "error arg1"
        runnable({ assertTrue(true, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertTrue(true, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertFalse should throw"() {
        when:
        assertFalse(true)

        then:
        thrown(AssertException)
    }

    def "assertFalse constant message should throw with message"() {
        when:
        assertFalse(true, "constant message")

        then:
        def error = thrown(AssertException)
        error.message == "constant message"
    }

    def "assertFalse with error message should throw on false value with formatted message"() {
        when:
        assertFalse(true, { -> String.format(format, arg) })

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        format     | arg   || expectedMessage
        "error %s" | "msg" || "error msg"
        "code %d"  | 1     || "code 1"
        "code"     | null  || "code"
    }

    def "assertFalse with different number of error args throws assertions"() {
        when:
        toRun.run()

        then:
        def error = thrown(AssertException)
        error.message == expectedMessage

        where:
        toRun                                                                            | expectedMessage
        runnable({ assertFalse(true, "error %s", "arg1") })                       | "error arg1"
        runnable({ assertFalse(true, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertFalse(true, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertFalse with different number of error args but false does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                             | expectedMessage
        runnable({ assertFalse(false, "error %s", "arg1") })                       | "error arg1"
        runnable({ assertFalse(false, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ assertFalse(false, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertValidName should not throw on valid names"() {
        when:
        assertValidName(name)

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
        assertValidName(name)

        then:
        def error = thrown(AssertException)
        error.message == "Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]* - was '${name}'"

        where:
        name   | _
        "0abc" | _
        "���"  | _
        "_()"  | _
    }

    // Spock data tables cant cope with { x } syntax but it cna do this
    Runnable runnable(Runnable r) {
        return r
    }

}
