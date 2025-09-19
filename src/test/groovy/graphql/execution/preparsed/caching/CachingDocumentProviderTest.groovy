package graphql.execution.preparsed.caching

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.parser.Parser
import spock.lang.Specification

import java.util.function.Function

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
        def executionInput = ExecutionInput.newExecutionInput(heroQuery1)
                .operationName("HeroNameQuery").build()

        def er = graphQL.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]
    }

    def "integration still works when caffeine is not on the class path"() {
        // we fake out the test here saying its NOT on the classpath
        def cache = new CaffeineDocumentCache(false)
        def cachingDocumentProvider = new CachingDocumentProvider(cache)
        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(cachingDocumentProvider)
                .build()
        when:
        def executionInput = ExecutionInput.newExecutionInput(heroQuery1)
                .operationName("HeroNameQuery").build()

        def er = graphQL.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data == [hero: [name: "R2-D2"]]
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

        def ei = ExecutionInput.newExecutionInput("query q { f }").build()
        def callback = new CountingDocProvider()

        when:
        def cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        def documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // cached
        callback.count == 1

        when:
        cache.clear()
        cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // after cleared cached
        callback.count == 2

    }

    def "when caching is not present then parse and validated function is always called"() {
        def cache = new CaffeineDocumentCache(false)
        def cachingDocumentProvider = new CachingDocumentProvider(cache)

        def ei = ExecutionInput.newExecutionInput("query q { f }").build()
        def callback = new CountingDocProvider()

        when:
        def cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        def documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 1

        when:
        cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        // not cached
        callback.count == 2

        when:
        cache.clear()
        cf = cachingDocumentProvider.getDocumentAsync(ei, callback)
        documentEntry = cf.join()

        then:
        !documentEntry.hasErrors()
        documentEntry.document != null
        callback.count == 3
    }
}
