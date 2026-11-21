package graphql.schema

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.conditional.ConditionalNodeDecision
import graphql.language.Field
import graphql.normalized.NormalizedDocument
import graphql.validation.QueryComplexityLimits
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class LookaheadFieldLimitTest extends Specification {
    def originalDefaults = QueryComplexityLimits.getDefaultLimits()

    def cleanup() {
        QueryComplexityLimits.setDefaultLimits(originalDefaults)
    }

    def "the exact limit includes every concrete path and permits empty leaf selections"() {
        given:
        def selection = selectionSet(nestedSelection(depth), limits(limit))

        expect:
        selection.fields.size() == limit
        selection.getFields('**').size() == limit
        selection.fields.every { it.objectTypeNames.size() == 1 }

        where:
        depth | limit
        1     | 2
        3     | 14
    }

    def "all flat inspection methods enforce the limit even when a glob matches nothing"() {
        given:
        def selection = selectionSet(nestedSelection(3), limits(13))
        // Warm the immediate cache before starting the bounded full traversal.
        assert selection.immediateFields.size() == 2

        when:
        inspect(selection)

        then:
        def failure = thrown(IllegalStateException)
        failure.message == 'Lookahead field enumeration exceeds QueryComplexityLimits.maxFieldOccurrences (limit=13)'

        where:
        inspect << [
                { it.fields },
                { it.getFields('**') },
                { it.getFields('missing') },
                { it.contains('missing') },
                { it.containsAnyOf('missing', 'value') },
                { it.containsAllOf('missing', 'value') },
                { it.fieldsGroupedByResultKey },
                { it.getFieldsGroupedByResultKey('missing') }
        ]
    }

    def "retrying a failed traversal charges cached child batches again"() {
        given:
        def selection = selectionSet(nestedSelection(3), limits(13))

        when:
        selection.fields

        then:
        thrown(IllegalStateException)

        when:
        selection.fields

        then:
        thrown(IllegalStateException)
        selection.immediateFields.size() == 2
        selection.immediateFields[0].selectionSet.fields.size() == 6
    }

    @Timeout(10)
    def "nested interfaces stop before materializing exponentially many paths"() {
        given:
        def decisions = new AtomicInteger()
        // Without a bound, this small AST has more than 68 billion concrete field occurrences.
        def selection = selectionSet(nestedSelection(35), limits(128), 2, decisions, 129)

        when:
        selection.fields

        then:
        thrown(IllegalStateException)
        decisions.get() == 129
    }

    def "the remaining budget also bounds a wide child batch before it is cached"() {
        given:
        def decisions = new AtomicInteger()
        def selection = selectionSet(nestedSelection(2), limits(150), 100, decisions, 151)
        assert selection.immediateFields.size() == 100

        when:
        selection.fields

        then:
        thrown(IllegalStateException)
        decisions.get() == 151
    }

    def "immediate enumeration also rejects an oversized concrete type expansion"() {
        given:
        def selection = selectionSet('value', limits(99), 100)

        when:
        selection.immediateFields

        then:
        thrown(IllegalStateException)

        when:
        selection.immediateFields

        then:
        thrown(IllegalStateException)
    }

    def "merged duplicates count once per concrete parent and response key"() {
        given:
        def selection = selectionSet('value value alias: value', limits(4))

        expect:
        selection.fields*.resultKey == ['value', 'alias', 'value', 'alias']
        selection.fieldsGroupedByResultKey*.value*.size() == [2, 2]
    }

    def "concurrent repeated reads do not share a mutable enumeration budget"() {
        given:
        def selection = selectionSet(nestedSelection(3), limits(14))

        when:
        def reads = (1..12).collect { CompletableFuture.supplyAsync { selection.fields.size() } }

        then:
        reads.collect { it.join() } == [14] * 12
        selection.getFields('**').size() == 14
    }

    def "request limits override JVM defaults and NONE disables the enumeration bound"() {
        given:
        QueryComplexityLimits.setDefaultLimits(limits(globalLimit))
        def requestLimits = requestLimit == null ? null : limits(requestLimit)
        def selection = selectionSet(nestedSelection(3), requestLimits)

        when:
        def count = 0
        IllegalStateException failure = null
        try {
            count = selection.fields.size()
        } catch (IllegalStateException exception) {
            failure = exception
        }

        then:
        (failure != null) == rejected
        rejected || count == 14

        where:
        globalLimit       | requestLimit       | rejected
        13                | null               | true
        14                | null               | false
        13                | 14                 | false
        14                | 13                 | true
        13                | Integer.MAX_VALUE  | false
        Integer.MAX_VALUE | null               | false
    }

    private static QueryComplexityLimits limits(int count) {
        QueryComplexityLimits.NONE.transform { it.maxFieldOccurrences(count) }
    }

    private static String nestedSelection(int depth) {
        def selection = 'value'
        (1..<depth).each { selection = "next { $selection }" }
        selection
    }

    private static DataFetchingFieldSelectionSet selectionSet(String selection, QueryComplexityLimits limits,
                                                              int types = 2, AtomicInteger decisions = new AtomicInteger(),
                                                              int safetyBound = Integer.MAX_VALUE) {
        def implementations = (0..<types).collect { "type N$it implements Node { next: Node value: String }" }.join('\n')
        def schema = TestUtil.schema("""
            type Query { node: Node }
            interface Node { next: Node value: String }
            $implementations
        """)
        DataFetchingFieldSelectionSet captured
        DataFetcher fetcher = { env -> captured = env.selectionSet; null }
        def registry = schema.codeRegistry.transform { it.dataFetcher(FieldCoordinates.coordinates('Query', 'node'), fetcher) }
        def graphQL = GraphQL.newGraphQL(schema.transform { it.codeRegistry(registry) }).build()
        def input = ExecutionInput.newExecutionInput("{ node { $selection } }")
                .graphQLContext([(NormalizedDocument.ENABLED): true]).build()
        if (limits != null) {
            input.graphQLContext.put(QueryComplexityLimits.KEY, limits)
        }
        input.graphQLContext.put(ConditionalNodeDecision, { env ->
            def source = env.directivesContainer
            if (source instanceof Field && source.name != 'node') {
                assert decisions.incrementAndGet() <= safetyBound: 'Enumeration continued past the expected bound'
            }
            true
        } as ConditionalNodeDecision)
        def result = graphQL.execute(input)
        assert result.errors.empty
        captured
    }
}
