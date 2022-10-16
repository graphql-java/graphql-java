package graphql

import graphql.language.SourceLocation
import spock.lang.Specification

/**
 * Most of the tests are actually in ExecutionResultImplTest since this is the actual impl
 */
class ExecutionResultTest extends Specification {

    def error1 = new InvalidSyntaxError(new SourceLocation(966, 964), "Yowza")

    def "can use builder to build it"() {
        when:
        ExecutionResult er = ExecutionResult.newExecutionResult().data([a: "b"]).addError(error1).addExtension("x", "y").build()
        then:
        er.data == [a: "b"]
        er.errors == [error1]
        er.extensions == [x: "y"]
    }

    def "can transform"() {
        when:
        ExecutionResult er = ExecutionResult.newExecutionResult().data([a: "b"]).addError(error1).addExtension("x", "y").build()
        er = er.transform({ bld -> bld.addExtension("foo", "bar") })
        then:
        er.data == [a: "b"]
        er.errors == [error1]
        er.extensions == [x: "y", foo: "bar"]
    }
}
