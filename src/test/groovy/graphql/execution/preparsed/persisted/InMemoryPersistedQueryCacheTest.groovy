package graphql.execution.preparsed.persisted

import spock.lang.Specification

class InMemoryPersistedQueryCacheTest extends Specification {

    def "can be build as expected"() {
        def inMemoryPersistedQueryCache = InMemoryPersistedQueryCache.newInMemoryPersistedQueryCache()
                .addQuery("hash123", "query { oneTwoThree }")
                .addQuery("hash456", "query { fourFiveSix }")
                .build()

        when:
        def knownQueries = inMemoryPersistedQueryCache.getKnownQueries()
        then:
        knownQueries == [hash123: "query { oneTwoThree }", hash456: "query { fourFiveSix }"]
    }
}
