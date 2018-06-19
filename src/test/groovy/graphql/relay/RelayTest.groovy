package graphql.relay

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RelayTest extends Specification {

    def urlSafe(String s) {
        return s == URLEncoder.encode(s, StandardCharsets.UTF_8.name())
    }

    def "global id encoding is consistent and url-safe"() {
        given:
        def relay = new Relay()
        def type = "Type"

        expect:
        def globalId = relay.toGlobalId(type, id)
        def idResolved = relay.fromGlobalId(globalId)
        type == idResolved.type && id == idResolved.id && urlSafe(globalId)

        where:
        id                 || base64UrlSafeReference
        'null'             || 'VHlwZTpudWxs'
        ''                 || 'VHlwZTo'
        '1'                || 'VHlwZTox'
        '99'               || 'VHlwZTo5OQ'
        'abc'              || 'VHlwZTphYmM'
        '.,-:;_=?()/&%$§!' || 'VHlwZTouLC06O189PygpLyYlJMKnIQ'
        '~abc'             || 'VHlwZTp-YWJj'
        '?_graphql'        || 'VHlwZTo_X2dyYXBocWw'
    }

}
