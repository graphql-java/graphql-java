package graphql.scalar

import spock.lang.Specification

class CoercingUtilTest extends Specification {

    def "isNumberIsh test case"() {
        when:
        CoercingUtil.isNumberIsh(object)

        then:
        noExceptionThrown()

        where:
        object      || expectedMessage
        1           || true
        -1          || true
        1.1         || true
        "1.1"       || true
        "1"         || true
        "-1"        || true
        "notNumber" || false
        null        || true
    }

}
