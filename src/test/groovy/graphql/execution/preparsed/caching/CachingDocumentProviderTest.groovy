package graphql.execution.preparsed.caching


import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import spock.lang.Specification

import java.time.Duration
import java.util.function.Function

import static graphql.ExecutionInput.newExecutionInput

class CachingDocumentProviderTest extends Specification {
    private String heroQuery1

    void setup() {
        heroQuery1 = """        
            query HeroNameQuery {
              hero {
                name
              }
            }
        """
    }

    def "basic integration test"() {

        def cachingDocumentProvider = new CachingDocumentProvider()
        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(cachingDocumentProvider)
                .build()

        when:
        def executionInput = newExecutionInput(heroQuery1)
                .operationName("HeroNameQuery").build()

        def er = graphQL.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]

        cachingDocumentProvider.getDocumentCache() instanceof CaffeineDocumentCache
    }

    def "different outcomes are cached correctly integration test"() {

        def cachingDocumentProvider = new CachingDocumentProvider()
        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(cachingDocumentProvider)
                .build()


        def query = """        
            query HeroNameQuery {
              hero {
                name
              }
            }
            query HeroNameQuery2 {
              hero {
                nameAlias : name
              }
            }
        """
        def invalidQuery = """
            query InvalidQuery {
              hero {
                nameX
              }
            }
        """
        when:
        def ei = newExecutionInput(query).operationName("HeroNameQuery").build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]

        when:
        ei = newExecutionInput(query).operationName("HeroNameQuery2").build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [hero: [nameAlias: "R2-D2"]]

        when:
        ei = newExecutionInput(invalidQuery).operationName("InvalidQuery").build()
        er = graphQL.execute(ei)

        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validation error (FieldUndefined@[hero/nameX]) : Field 'nameX' in type 'Character' is undefined"

        // now do them all again and they are cached but the outcome is the same

        when:
        ei = newExecutionInput(query).operationName("HeroNameQuery").build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]

        when:
        ei = newExecutionInput(query).operationName("HeroNameQuery2").build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [hero: [nameAlias: "R2-D2"]]

        when:
        ei = newExecutionInput(invalidQuery).operationName("InvalidQuery").build()
        er = graphQL.execute(ei)

        then:
        !er.errors.isEmpty()
        er.errors[0].message == "Validation error (FieldUndefined@[hero/nameX]) : Field 'nameX' in type 'Character' is undefined"
    }

    def "integration still works when caffeine is not on the class path"() {
        when:
        // we fake out the test here saying its NOT on the classpath
        def cache = new CaffeineDocumentCache(false)
        def cachingDocumentProvider = new CachingDocumentProvider(cache)
        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(cachingDocumentProvider)
                .build()
        def executionInput = newExecutionInput(heroQuery1)
                .operationName("HeroNameQuery").build()

        ExecutionResult er = null
        for (int i = 0; i < count; i++) {
            er = graphQL.execute(executionInput)
            assert er.data == [hero: [name: "R2-D2"]]
        }


        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]

        where:
        count || _
        1     || _
        5     || _
    }

    def "integration of a custom cache"() {
        when:

        def cache = new DocumentCache() {
            // not really useful in production since its unbounded
            def map = new HashMap<DocumentCache.DocumentCacheKey,PreparsedDocumentEntry>()

            @Override
            PreparsedDocumentEntry get(DocumentCache.DocumentCacheKey key, Function<DocumentCache.DocumentCacheKey, PreparsedDocumentEntry> mappingFunction) {
                return map.computeIfAbsent(key,mappingFunction)
            }

            @Override
            boolean isNoop() {
                return false
            }

            @Override
            void invalidateAll() {
                map.clear()
            }
        }
        // a custom cache in play
        def cachingDocumentProvider = new CachingDocumentProvider(cache)
        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(cachingDocumentProvider)
                .build()
        def executionInput = newExecutionInput(heroQuery1)
                .operationName("HeroNameQuery").build()

        ExecutionResult er = null
        for (int i = 0; i < count; i++) {
            er = graphQL.execute(executionInput)
            assert er.data == [hero: [name: "R2-D2"]]
        }


        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]

        where:
        count || _
        1     || _
        5     || _
    }

    class CountingDocProvider implements Function<ExecutionInput, PreparsedDocumentEntry> {
        int count = 0

        @Override
        PreparsedDocumentEntry apply(ExecutionInput executionInput) {
            count++
            def document = Parser.parse(executionInput.query)
            return new PreparsedDocumentEntry(document)
        }
    }

    def "caching happens and the parse and validated function is avoided"() {
        def cache = new CaffeineDocumentCache(true)
        def cachingDocumentProvider = new CachingDocumentProvider(cache)

        def ei = newExecutionInput("query q { f }").build()
        def callback = new CountingDocProvider()

        when:
        def documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // cached
        callback.count == 1

        when:
        cache.invalidateAll()
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // after cleared cached
        callback.count == 2

    }

    def "when caching is not present then parse and validated function is always called"() {
        def cache = new CaffeineDocumentCache(false)
        def cachingDocumentProvider = new CachingDocumentProvider(cache)

        def ei = newExecutionInput("query q { f }").build()
        def callback = new CountingDocProvider()

        when:
        def documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // not cached
        callback.count == 2

        when:
        cache.invalidateAll()
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 3
    }

    def "time can pass and entries can expire and the code handles that"() {
        long nanoTime = 0
        Ticker ticker = { return nanoTime }
        def caffeineCache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterAccess(Duration.ofMinutes(2))
                .<DocumentCache.DocumentCacheKey, PreparsedDocumentEntry> build()
        def documentCache = new CaffeineDocumentCache(caffeineCache)

        // note this is a custom caffeine instance pass in
        def cachingDocumentProvider = new CachingDocumentProvider(documentCache)

        def ei = newExecutionInput("query q { f }").build()
        def callback = new CountingDocProvider()

        when:
        def documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        //
        // this is kinda testing Caffeine but I am also trying to make sure that th wrapper
        // code does the mappingFunction if its expired
        //
        // advance time
        //
        nanoTime += Duration.ofMinutes(5).toNanos()
        documentEntry = cachingDocumentProvider.getDocumentAsync(ei, callback).join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 2
    }
}
