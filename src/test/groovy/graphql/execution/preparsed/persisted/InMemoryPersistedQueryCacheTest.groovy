package graphql.execution.preparsed.persisted

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import spock.lang.Specification

import static graphql.language.AstPrinter.printAstCompact

class InMemoryPersistedQueryCacheTest extends Specification {

    def mkEI(String hash, String query) {
        ExecutionInput.newExecutionInput().query(query).extensions([persistedQuery: [sha256Hash: hash, version: 1]]).build()
    }

    PersistedQueryCacheMiss onMiss = {
        String query ->
            def doc = new Parser().parseDocument(query)
            return new PreparsedDocumentEntry(doc)
    }

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

    def "Uses the query from the execution input if the query ID wasn't in the cache"() {
        def inMemCache = InMemoryPersistedQueryCache.newInMemoryPersistedQueryCache().build()
        def hash = "thisisahash"
        def ei = mkEI(hash, "query { oneTwoThreeFour }")

        when:
        def getDoc = inMemCache.getPersistedQueryDocumentAsync(hash, ei, onMiss).join()
        def doc = getDoc.document
        then:
        printAstCompact(doc) == "{oneTwoThreeFour}"
    }

    def "When there's a cache miss, uses the query from known queries if the execution input's query is the APQ Marker"() {
        def hash = "somehash"
        def inMemCache = InMemoryPersistedQueryCache.newInMemoryPersistedQueryCache()
                .addQuery(hash, "{foo bar baz}")
                .build()
        def ei = mkEI(hash, PersistedQuerySupport.PERSISTED_QUERY_MARKER)
        when:
        def getDoc = inMemCache.getPersistedQueryDocumentAsync(hash, ei, onMiss).join()
        def doc = getDoc.document
        then:
        printAstCompact(doc) == "{foo bar baz}"
    }

    def "known queries take precedence over the query text in the execution input"() {
        def hash = "knownhash"
        def inMemCache = InMemoryPersistedQueryCache.newInMemoryPersistedQueryCache()
                .addQuery(hash, "{foo bar baz}")
                .build()
        def ei = mkEI(hash, "{ somethingElse }")

        when:
        def getDoc = inMemCache.getPersistedQueryDocumentAsync(hash, ei, onMiss).join()
        def doc = getDoc.document
        then:
        printAstCompact(doc) == "{foo bar baz}"

        // a later request that only sends the id must not see the text from the earlier request
        when:
        def laterEi = mkEI(hash, PersistedQuerySupport.PERSISTED_QUERY_MARKER)
        def laterDoc = inMemCache.getPersistedQueryDocumentAsync(hash, laterEi, onMiss).join().document
        then:
        printAstCompact(laterDoc) == "{foo bar baz}"
    }
}
