package graphql.util


import spock.lang.Specification

class LogKitTest extends Specification {

    def "logger has a prefixed name"() {
        when:
        def logger = LogKit.getNotPrivacySafeLogger(LogKitTest.class)
        then:
        logger.getName() == "notprivacysafe.graphql.util.LogKitTest"
    }
}
