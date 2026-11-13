package graphql

import spock.lang.Ignore
import spock.lang.Specification

/**
 * This is only really useful for testing the gradle builds etc...
 * and in general would not be needed to test graphql-java
 */
@Ignore
class AlwaysFailsTest extends Specification{

    def "this test fails"() {
        when:
        true
        then:
        assert false
    }

    def "and this test always fails"() {
        when:
        true
        then:
        assert false
    }

}
