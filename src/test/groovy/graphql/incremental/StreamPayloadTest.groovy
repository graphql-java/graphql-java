package graphql.incremental

import spock.lang.Specification

class StreamPayloadTest extends Specification {
    def "null data is included"() {
        def payload = StreamPayload.newStreamedItem()
                .items(null)
                .build()

        when:
        def spec = payload.toSpecification()

        then:
        spec == [
                items: null,
                path : null,
        ]
    }
}
