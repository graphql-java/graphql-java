package graphql.execution

import graphql.Directives
import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.conditional.ConditionalDirectiveCoercionException
import graphql.execution.conditional.ConditionalNodes
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.pubsub.CapturingSubscriber
import graphql.incremental.IncrementalExecutionResult
import graphql.language.Field
import graphql.language.SourceLocation
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING

class ConditionalDirectiveCoercionTest extends Specification {

    def "root #operation collection rejects null #directive on #selection before fetching"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = TestUtil.graphQL('type Query { value: String } type Mutation { value: String }',
                [Query: [value: fetcher], Mutation: [value: fetcher]]).build()
        def condition = '@' + directive + '(if: $flag)'
        def query = operation + '($flag: Boolean = true) { ' + selection.replace('@CONDITION', condition) + ' }'
        if (selection.contains('...Fields')) {
            query += ' fragment Fields on ' + (operation == 'query' ? 'Query' : 'Mutation') + ' { value }'
        }

        when:
        def result = execute(graphQL, query)

        then:
        calls == 0
        result.dataPresent
        result.data == null
        assertError(result, query, null)

        where:
        [operation, directive, selection] << [
                ['query', 'mutation'], ['skip', 'include'],
                ['value @CONDITION', '... @CONDITION { value }', '...Fields @CONDITION']
        ].combinations()
    }

    def "nested #directive errors respect #parentType with async=#async"() {
        given:
        int children = 0
        DataFetcher parent = { async ? CompletableFuture.supplyAsync { [child: 'value'] } : [child: 'value'] }
        def graphQL = TestUtil.graphQL('type Query { good: String parent: ' + parentType + ' } type Parent { child: String }',
                [Query: [good: { 'good' } as DataFetcher, parent: parent],
                 Parent: [child: { children++; 'unexpected' } as DataFetcher]]).build()
        def query = 'query($flag: Boolean = true) { good alias: parent { child @' + directive + '(if: $flag) } }'

        when:
        def result = execute(graphQL, query)

        then:
        children == 0
        result.dataPresent
        result.data == (parentType == 'Parent!' ? null : [good: 'good', alias: null])
        assertError(result, query, ['alias'])

        where:
        [directive, parentType, async] << [['skip', 'include'], ['Parent', 'Parent!'], [false, true]].combinations()
    }

    def "nested fragments report the enclosing response path for #selection"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { parent: Parent } type Parent { child: String }').build()
        def query = 'query($flag: Boolean = true) { alias: parent { ' + selection + ' } } fragment Fields on Parent { child }'

        when:
        def result = execute(graphQL, query, [parent: [child: 'value']])

        then:
        result.data == [alias: null]
        assertError(result, query, ['alias'])

        where:
        selection << ['...Fields @skip(if: $flag)', '... on Parent @include(if: $flag) { ...Fields }']
    }

    def "list item failures propagate through #listType"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { good: String parents: ' + listType + ' } type Parent { child: String }').build()
        def query = 'query($flag: Boolean = true) { good alias: parents { child @include(if: $flag) } }'

        when:
        def result = execute(graphQL, query, [good: 'good', parents: [[child: 'value']]])

        then:
        result.data == expected
        assertError(result, query, ['alias', 0])

        where:
        listType     | expected
        '[Parent]'   | [good: 'good', alias: [null]]
        '[Parent]!'  | [good: 'good', alias: [null]]
        '[Parent!]'  | [good: 'good', alias: null]
        '[Parent!]!' | null
    }

    def "mutation preserves completed siblings and only continues past a nullable failure with #parentType"() {
        given:
        def calls = []
        def graphQL = TestUtil.graphQL('type Query { unused: String } type Mutation { before: String parent: ' + parentType + ' after: String } type Parent { child: String }',
                [Mutation: [before: { calls << 'before'; 'before' } as DataFetcher,
                            parent: { calls << 'parent'; [child: 'value'] } as DataFetcher,
                            after: { calls << 'after'; 'after' } as DataFetcher]]).build()
        def query = 'mutation($flag: Boolean = true) { before alias: parent { child @skip(if: $flag) } after }'

        when:
        def result = execute(graphQL, query)

        then:
        calls == expectedCalls
        result.data == expectedData
        assertError(result, query, ['alias'])

        where:
        parentType | expectedCalls                 | expectedData
        'Parent'   | ['before', 'parent', 'after']   | [before: 'before', alias: null, after: 'after']
        'Parent!'  | ['before', 'parent']            | null
    }

    def "subscription #directive failures produce an error result for each #parentType event"() {
        given:
        def events = [1, 2].collect { DataFetcherResult.newResult().data([child: 'value']).extensions([event: it]).build() }
        DataFetcher source = { Flux.fromIterable(events) }
        def graphQL = TestUtil.graphQL('type Query { unused: String } type Subscription { event: ' + parentType + ' } type Parent { child: String }',
                [Subscription: [event: source]]).build()
        def query = 'subscription($flag: Boolean = true) { alias: event { child @' + directive + '(if: $flag) } }'
        def initial = execute(graphQL, query)
        def subscriber = new CapturingSubscriber<ExecutionResult>()

        when:
        initial.data.subscribe(subscriber)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilTrue(subscriber.isDone())

        then:
        initial.errors.empty
        subscriber.throwable == null
        subscriber.events.size() == 2
        subscriber.events.each { result ->
            assert result.dataPresent
            assert result.data == (parentType == 'Parent!' ? null : [alias: null])
            assertError(result, query, ['alias'])
        }
        subscriber.events*.extensions*.event.sort() == [1, 2]

        where:
        [directive, parentType] << [['skip', 'include'], ['Parent', 'Parent!']].combinations()
    }

    def "unexpected subscription completion failures still terminate the stream"() {
        given:
        def failure = new IllegalStateException('Unexpected type resolver failure')
        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring('Subscription').dataFetcher('event', { Flux.just([:]) }))
                .type(newTypeWiring('Parent').dataFetcher('node', { CompletableFuture.completedFuture([id: '1']) }))
                .type(newTypeWiring('Node').typeResolver { throw failure })
        def graphQL = TestUtil.graphQL('''type Query { unused: String }
            type Subscription { event: Parent }
            type Parent { node: Node }
            interface Node { id: ID }
            type Child implements Node { id: ID }
        ''', wiring).build()
        def initial = graphQL.execute('subscription { event { node { id } } }')
        def subscriber = new CapturingSubscriber<ExecutionResult>()

        when:
        initial.data.subscribe(subscriber)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilTrue(subscriber.isDone())

        then:
        subscriber.events.empty
        subscriber.throwable != null
        subscriber.throwable.cause.is(failure)
    }

    def "deferred #parentType failure retains the directive error location"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { good: String parent: ' + parentType + ' } type Parent { child: String }').build()
        def query = 'query($flag: Boolean = true) { good ... @defer { alias: parent { child @skip(if: $flag) } } }'
        def input = ExecutionInput.newExecutionInput(query).variables([flag: null])
                .root([good: 'good', parent: [child: 'value']]).graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()

        when:
        IncrementalExecutionResult result = graphQL.execute(input)
        def subscriber = new CapturingSubscriber()
        result.incrementalItemPublisher.subscribe(subscriber)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilTrue(subscriber.isDone())
        def patches = subscriber.events.collectMany { it.toSpecification().incremental ?: [] }

        then:
        result.data == [good: 'good']
        result.errors.empty
        subscriber.throwable == null
        patches.size() == 1
        patches[0].data == (parentType == 'Parent!' ? null : [alias: null])
        patches[0].errors.size() == 1
        patches[0].errors[0].message.contains("Invalid argument for directive '@skip'")
        patches[0].errors[0].path == ['alias']
        patches[0].errors[0].locations == [[line: 1, column: query.lastIndexOf('$flag') + 1]]

        where:
        parentType << ['Parent', 'Parent!']
    }

    def "conditional failure does not stall DataLoader dispatch with #mode and #parentType"() {
        given:
        BatchLoader<String, Object> batchLoader = { keys ->
            CompletableFuture.completedFuture(keys.collect { it == 'parent' ? [child: 'value'] : 'good' })
        }
        def registry = DataLoaderRegistry.newRegistry().register('values', DataLoaderFactory.newDataLoader(batchLoader)).build()
        DataFetcher parent = { env -> env.getDataLoader('values').load('parent') }
        DataFetcher good = { env -> env.getDataLoader('values').load('good') }
        def graphQL = TestUtil.graphQL('type Query { parent: ' + parentType + ' good: String } type Parent { child: String }',
                [Query: [parent: parent, good: good]]).build()
        def query = 'query($flag: Boolean = true) { alias: parent { child @skip(if: $flag) } good }'
        def input = ExecutionInput.newExecutionInput(query).variables([flag: null]).dataLoaderRegistry(registry)
                .graphQLContext(mode == null ? [:] : [(mode): true]).build()

        when:
        def result = graphQL.executeAsync(input).get(10, TimeUnit.SECONDS)

        then:
        result.data == (parentType == 'Parent!' ? null : [alias: null, good: 'good'])
        assertError(result, query, ['alias'])

        where:
        [mode, parentType] << [[null, ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING], ['Parent', 'Parent!']].combinations()
    }

    def "defaults and Boolean variables keep #directive behavior for #variables"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { field: Int }').build()
        def query = 'query($flag: Boolean = true) { field @' + directive + '(if: $flag) }'
        def input = ExecutionInput.newExecutionInput(query).variables(variables).root([field: 1]).build()

        when:
        def result = graphQL.execute(input)

        then:
        result.errors.empty
        result.data == ((directive == 'include') == variables.getOrDefault('flag', true) ? [field: 1] : [:])

        where:
        [directive, variables] << [['skip', 'include'], [[:], [flag: true], [flag: false]]].combinations()
    }

    def "unreached conditions are not coerced for #selection"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { field: Int parent: Parent } type Parent { field: Int }').build()
        def query = 'query($flag: Boolean = true) { ' + selection + ' }'

        when:
        def result = execute(graphQL, query)

        then:
        result.errors.empty
        result.data == expected

        where:
        selection                                                         | expected
        'field @skip(if: true) @include(if: $flag)'                         | [:]
        'parent { field @skip(if: $flag) }'                                | [parent: null]
        '... @skip(if: true) { field @include(if: $flag) }'                  | [:]
    }

    def "normalization keeps unknown variables undecided but rejects coerced null"() {
        given:
        def schema = TestUtil.schema('type Query { field: Int }')
        def document = Parser.parse('query($flag: Boolean = true) { field @skip(if: $flag) }')
        Field field = document.definitions[0].selectionSet.selections[0] as Field

        expect:
        new ConditionalNodes().shouldIncludeWithoutVariables(field) == null

        when:
        ExecutableNormalizedOperationFactory.createExecutableNormalizedOperationWithRawVariables(schema, document, null, RawVariables.of([flag: null]))

        then:
        def exception = thrown(ConditionalDirectiveCoercionException)
        exception.cause instanceof NonNullableValueCoercedAsNullException
        exception.locations == [field.directives[0].arguments[0].value.sourceLocation]
    }

    def "root failure completes operation instrumentation"() {
        given:
        def operationContext = Mock(InstrumentationContext)
        def instrumentation = Spy(SimplePerformantInstrumentation) {
            beginExecuteOperation(_, _) >> operationContext
        }
        def graphQL = TestUtil.graphQL('type Query { field: Int }').instrumentation(instrumentation).build()
        def query = 'query($flag: Boolean = true) { field @skip(if: $flag) }'

        when:
        def result = execute(graphQL, query)

        then:
        1 * operationContext.onDispatched()
        1 * operationContext.onCompleted({ it.dataPresent && it.data == null && it.errors.size() == 1 }, _ as ConditionalDirectiveCoercionException)
        assertError(result, query, null)
    }

    def "disabled error propagation keeps a failed non-null parent in its response position"() {
        given:
        def originallyEnabled = Directives.isExperimentalDisableErrorPropagationDirectiveEnabled()
        Directives.setExperimentalDisableErrorPropagationEnabled(true)
        def graphQL = TestUtil.graphQL('type Query { good: String parent: Parent! } type Parent { child: String }').build()
        def query = 'query($flag: Boolean = true) @experimental_disableErrorPropagation { good alias: parent { child @skip(if: $flag) } }'

        when:
        def result = execute(graphQL, query, [good: 'good', parent: [child: 'value']])

        then:
        result.data == [good: 'good', alias: null]
        assertError(result, query, ['alias'])

        cleanup:
        Directives.setExperimentalDisableErrorPropagationEnabled(originallyEnabled)
    }

    def "non-null variable declarations still fail before operation execution"() {
        given:
        def graphQL = TestUtil.graphQL('type Query { field: Int }').build()

        when:
        def result = execute(graphQL, 'query($flag: Boolean!) { field @skip(if: $flag) }')

        then:
        !result.dataPresent
        result.errors.size() == 1
        result.errors[0] instanceof NonNullableValueCoercedAsNullException
    }

    def "conditional evaluation uses shared coercion for #condition even without a context"() {
        given:
        Field field = Parser.parse('{ field ' + condition + ' }').definitions[0].selectionSet.selections[0] as Field

        when:
        new ConditionalNodes().shouldInclude(field, [:], null, null)

        then:
        def exception = thrown(ConditionalDirectiveCoercionException)
        exception.cause instanceof NonNullableValueCoercedAsNullException
        exception.locations == [field.directives[0].arguments.empty ? field.directives[0].sourceLocation : field.directives[0].arguments[0].value.sourceLocation]

        where:
        condition << ['@include(if: $missing)', '@skip']
    }

    def "unexpected coercion failures are not converted to GraphQL errors"() {
        given:
        Field field = Parser.parse('{ field @include(if: $flag) }').definitions[0].selectionSet.selections[0] as Field

        when:
        // This API expects already-coerced variables. A wrong runtime type is a caller error.
        new ConditionalNodes().shouldInclude(field, [flag: 'not a Boolean'], null, null)

        then:
        thrown(ClassCastException)
    }

    private ExecutionResult execute(GraphQL graphQL, String query, Map root = [:]) {
        def input = ExecutionInput.newExecutionInput(query).variables([flag: null]).root(root).build()
        graphQL.executeAsync(input).get(10, TimeUnit.SECONDS)
    }

    private void assertError(ExecutionResult result, String query, List path) {
        assert result.errors.size() == 1
        def error = result.errors[0]
        assert error.message.contains('Invalid argument for directive')
        assert error.errorType == ErrorType.DataFetchingException
        assert error.path == path
        assert error.locations == [new SourceLocation(1, query.lastIndexOf('$flag') + 1)]
    }
}
