package graphql.execution.preparsed.persisted

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import spock.lang.Specification

import java.util.function.Function

import static graphql.execution.preparsed.persisted.PersistedQuerySupport.PERSISTED_QUERY_MARKER
import static graphql.language.AstPrinter.printAstCompact

class ApolloPersistedQuerySupportTest extends Specification {

    def knownQueries = [
            "hash123": "query { oneTwoThree }",
            "hash456": "query { fourFiveSix }"
    ]

    // this cache will do a lookup, make the call back on miss and otherwise return cached values. And it
    // tracks call counts for assertions
    class CacheImplementation implements PersistedQueryCache {
        def map = [:]
        def keyCount = [:]
        def parseCount = [:]

        @Override
        PreparsedDocumentEntry getPersistedQueryDocument(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss) throws PersistedQueryNotFound {
            keyCount.compute(persistedQueryId, { k, v -> v == null ? 1 : v + 1 })
            PreparsedDocumentEntry entry = map.get(persistedQueryId) as PreparsedDocumentEntry
            if (entry != null) {
                return entry
            }
            parseCount.compute(persistedQueryId, { k, v -> v == null ? 1 : v + 1 })

            def queryText = knownQueries.get(persistedQueryId)
            // if its outside our know bounds then throw because we dont have one
            if (queryText == null) {
                throw new PersistedQueryNotFound(persistedQueryId)
            }
            def newDocEntry = onCacheMiss.apply(queryText)
            map.put(persistedQueryId, newDocEntry)
            return newDocEntry
        }
    }


    Function<ExecutionInput, PreparsedDocumentEntry> engineParser = {
        ExecutionInput ei ->
            def doc = new Parser().parseDocument(ei.getQuery())
            return new PreparsedDocumentEntry(doc)
    }


    def mkEI(String hash, String query) {
        ExecutionInput.newExecutionInput().query(query).extensions([persistedQuery: [sha256Hash: hash]]).build()
    }

    def "will call the callback on cache miss and then not after initial caching"() {

        CacheImplementation persistedQueryCache = new CacheImplementation()
        def apolloSupport = new ApolloPersistedQuerySupport(persistedQueryCache)

        when:
        def ei = mkEI("hash123", PERSISTED_QUERY_MARKER)
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount["hash123"] == 1
        persistedQueryCache.parseCount["hash123"] == 1

        when:
        ei = mkEI("hash123", PERSISTED_QUERY_MARKER)
        documentEntry = apolloSupport.getDocument(ei, engineParser)
        doc = documentEntry.getDocument()

        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount["hash123"] == 2
        persistedQueryCache.parseCount["hash123"] == 1 // only compiled once cause we had it
    }

    def "will act as a normal query if there and no hash id present"() {

        CacheImplementation persistedQueryCache = new CacheImplementation()
        def apolloSupport = new ApolloPersistedQuerySupport(persistedQueryCache)

        when:
        def ei = ExecutionInput.newExecutionInput("query { normal }").build()
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {normal}"
        persistedQueryCache.keyCount.size() == 0
        persistedQueryCache.parseCount.size() == 0
    }

    def "will use query hash in preference to query text"() {
        CacheImplementation persistedQueryCache = new CacheImplementation()
        def apolloSupport = new ApolloPersistedQuerySupport(persistedQueryCache)

        when:
        def ei = mkEI("hash123", "query {normal}")
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount["hash123"] == 1
        persistedQueryCache.parseCount["hash123"] == 1

    }

    def "will have error if we dont return any query text on cache miss"() {
        CacheImplementation persistedQueryCache = new CacheImplementation()

        def apolloSupport = new ApolloPersistedQuerySupport(persistedQueryCache)

        when:
        def ei = mkEI("nonExistedHash", PERSISTED_QUERY_MARKER)
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        then:
        documentEntry.getDocument() == null
        def gqlError = documentEntry.getErrors()[0]
        gqlError.getMessage() == "PersistedQueryNotFound"
        gqlError.getErrorType().toString() == "PersistedQueryNotFound"
        gqlError.getExtensions()["persistedQueryId"] == "nonExistedHash"
    }

    def "InMemoryPersistedQueryCache implementation works as expected with this class"() {

        InMemoryPersistedQueryCache persistedQueryCache = new InMemoryPersistedQueryCache(knownQueries)
        def apolloSupport = new ApolloPersistedQuerySupport(persistedQueryCache)

        when:
        def ei = mkEI("hash123", PERSISTED_QUERY_MARKER)
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"

        when:
        ei = mkEI("hash456", PERSISTED_QUERY_MARKER)
        documentEntry = apolloSupport.getDocument(ei, engineParser)
        doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {fourFiveSix}"

        when:
        ei = mkEI("nonExistent", PERSISTED_QUERY_MARKER)
        documentEntry = apolloSupport.getDocument(ei, engineParser)
        then:
        documentEntry.hasErrors()
    }
}