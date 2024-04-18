package graphql.incremental


import spock.lang.Specification

class DeferPayloadTest extends Specification {
    def "null data is included"() {
        def payload = DeferPayload.newDeferredItem()
                .data(null)
                .build()

        when:
        def spec = payload.toSpecification()

        then:
        spec == [
                data      : null,
                path      : null,
        ]
    }
}
