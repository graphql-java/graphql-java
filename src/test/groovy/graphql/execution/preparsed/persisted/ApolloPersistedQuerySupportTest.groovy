package graphql.execution.preparsed.persisted

import graphql.ExecutionInput
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import spock.lang.Specification

import java.util.function.Function

import static graphql.execution.preparsed.persisted.PersistedQuerySupport.PERSISTED_QUERY_MARKER
import static graphql.language.AstPrinter.printAstCompact

class ApolloPersistedQuerySupportTest extends Specification {

    def hashOne = "761cd68b4afb3a824091884dd6cb759b5d068102c293af5a0bc2023bbf8fdb9f"
    def hashTwo = "6e0a57aac0c8280588155e6d93436ad313e4c441b4e356703bdc297e32123d8f"

    def knownQueries = [
            (hashOne): "query { oneTwoThree }",
            (hashTwo): "query { fourFiveSix }",
            badHash  : "query { fourFiveSix }"
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
        def ei = mkEI(hashOne, PERSISTED_QUERY_MARKER)
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount[hashOne] == 1
        persistedQueryCache.parseCount[hashOne] == 1

        when:
        ei = mkEI(hashOne, PERSISTED_QUERY_MARKER)
        documentEntry = apolloSupport.getDocument(ei, engineParser)
        doc = documentEntry.getDocument()

        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount[hashOne] == 2
        persistedQueryCache.parseCount[hashOne] == 1 // only compiled once cause we had it
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
        def ei = mkEI(hashOne, "query {normal}")
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"
        persistedQueryCache.keyCount[hashOne] == 1
        persistedQueryCache.parseCount[hashOne] == 1

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
        def ei = mkEI(hashOne, PERSISTED_QUERY_MARKER)
        def documentEntry = apolloSupport.getDocument(ei, engineParser)
        def doc = documentEntry.getDocument()
        then:
        printAstCompact(doc) == "query {oneTwoThree}"

        when:
        ei = mkEI(hashTwo, PERSISTED_QUERY_MARKER)
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

    def "will have error if the calculated sha hash of the query does not match the persistedQueryId"() {
        def cache = new CacheImplementation()
        def apolloSupport = new ApolloPersistedQuerySupport(cache)
        when:
        def ei = mkEI("badHash", PERSISTED_QUERY_MARKER)
        def docEntry = apolloSupport.getDocument(ei, engineParser)
        then:
        docEntry.getDocument() == null
        def error = docEntry.getErrors()[0]
        error.message == "PersistedQueryIdInvalid"
        error.errorType.toString() == "PersistedQueryIdInvalid"
        error.getExtensions()["persistedQueryId"] == "badHash"
    }
}
