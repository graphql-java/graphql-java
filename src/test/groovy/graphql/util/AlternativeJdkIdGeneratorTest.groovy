package graphql.util

import spock.lang.Specification

class AlternativeJdkIdGeneratorTest extends Specification {
    def "can generate uuids"() {
        when:
        def set = new HashSet()
        for (int i = 0; i < 1000; i++) {
            set.add(AlternativeJdkIdGenerator.uuid().toString());
        }

        then:
        // should this fail - the universe has ended and has retracted back into the singularity
        set.size() == 1000
    }
}
