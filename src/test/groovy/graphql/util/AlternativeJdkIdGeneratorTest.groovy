package graphql.util

import spock.lang.Specification

class AlternativeJdkIdGeneratorTest extends Specification {
    def "can generate uuids"() {
        when:
        def uuid1 = AlternativeJdkIdGenerator.uuid()
        def uuid2 = AlternativeJdkIdGenerator.uuid()
        def uuid3 = AlternativeJdkIdGenerator.uuid()

        then:
        // should this fail - the universe has ended and has retracted back into the singularity
        uuid1.toString() != uuid2.toString()
        uuid1.toString() != uuid3.toString()
        uuid2.toString() != uuid3.toString()

    }
}
