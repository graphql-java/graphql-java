package graphql

import spock.lang.Specification

class AssertTest extends Specification {
    def "assertNotNull should not throw on none null value"() {
        when:
        Assert.assertNotNull("some object")

        then:
        noExceptionThrown()
    }

    def "assertNotNull should throw on null value"() {
        when:
        Assert.assertNotNull(null)

        then:
        thrown(AssertException)
    }

    def "assertNotNull constant message should throw on null value"() {
        when:
        Assert.assertNotNull(null, "constant message")

        then:
        def error = thrown(AssertException)
        error.message == "constant message"
    }

    def "assertNotNull with error message should not throw on none null value"() {
        when:
        Assert.assertNotNull("some object", { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertNotNull with error message should throw on null value with formatted message"() {
        when:
        Assert.assertNotNull(value, { -> String.format(format, arg) })

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
        runnable({ Assert.assertNotNull(null, "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertNotNull(null, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertNotNull(null, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertNotNull with different number of error args with non null does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                             | expectedMessage
        runnable({ Assert.assertNotNull("x", "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertNotNull("x", "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertNotNull("x", "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
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
        Assert.assertNotEmpty(value, { -> String.format(format, arg) })

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
        Assert.assertNotEmpty(["some object"], { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertTrue should not throw on true value"() {
        when:
        Assert.assertTrue(true, { -> "error message" })

        then:
        noExceptionThrown()
    }

    def "assertTrue with error message should throw on false value with formatted message"() {
        when:
        Assert.assertTrue(false, { -> String.format(format, arg) })

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
        Assert.assertTrue(false, "constant message")

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
        runnable({ Assert.assertTrue(false, "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertTrue(false, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertTrue(false, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertTrue with different number of error args but false does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                           | expectedMessage
        runnable({ Assert.assertTrue(true, "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertTrue(true, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertTrue(true, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertFalse should throw"() {
        when:
        Assert.assertFalse(true)

        then:
        thrown(AssertException)
    }

    def "assertFalse constant message should throw with message"() {
        when:
        Assert.assertFalse(true, "constant message")

        then:
        def error = thrown(AssertException)
        error.message == "constant message"
    }

    def "assertFalse with error message should throw on false value with formatted message"() {
        when:
        Assert.assertFalse(true, { -> String.format(format, arg) })

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
        runnable({ Assert.assertFalse(true, "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertFalse(true, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertFalse(true, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
    }

    def "assertFalse with different number of error args but false does not throw assertions"() {
        when:
        toRun.run()

        then:
        noExceptionThrown()

        where:
        toRun                                                                             | expectedMessage
        runnable({ Assert.assertFalse(false, "error %s", "arg1") })                       | "error arg1"
        runnable({ Assert.assertFalse(false, "error %s %s", "arg1", "arg2") })            | "error arg1 arg2"
        runnable({ Assert.assertFalse(false, "error %s %s %s", "arg1", "arg2", "arg3") }) | "error arg1 arg2 arg3"
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
        "���"  | _
        "_()"  | _
    }

    // Spock data tables cant cope with { x } syntax but it cna do this
    Runnable runnable(Runnable r) {
        return r
    }

}
