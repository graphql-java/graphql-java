package graphql.execution

import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.pubsub.CapturingSubscriber
import graphql.incremental.IncrementalExecutionResult
import graphql.language.SourceLocation
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.DataFetcher
import graphql.schema.GraphQLScalarType
import graphql.schema.LightDataFetcher
import graphql.schema.PropertyDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ArgumentCoercionExecutionTest extends Specification {

    def "invalid #argumentType argument prevents #operation resolver invocation with readsArguments=#readsArguments"() {
        given:
        int calls = 0
        DataFetcher fetcher = { env ->
            calls++
            if (readsArguments) {
                env.getArgument('arg')
            }
            'unexpected'
        }
        def graphQL = graphQLBuilder(argumentType, fetcher).build()
        def query = operation + '($v: String = "default") { alias: f(arg: ' + literal + ') good }'

        when:
        def result = executeValid(graphQL, query, [v: null])

        then:
        calls == 0
        result.data == [alias: null, good: 'good']
        assertCoercionError(result, ['alias'])
        result.errors[0].locations == [new SourceLocation(1, query.indexOf('alias:') + 1)]

        where:
        [operation, readsArguments, argument] << [
                ['query', 'mutation'],
                [false, true],
                [['String!', '$v'], ['[String!]!', '[$v]'], ['RequiredInput', '{a: $v}'],
                 ['ListInput', '{items: [$v]}'], ['[[String!]!]!', '[[$v]]']]
        ].combinations()
        argumentType = argument[0]
        literal = argument[1]
    }

    def "#operation argument failure propagates through non-null results"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = graphQLBuilder('[String!]!', fetcher, 'String!').build()

        when:
        def result = executeValid(graphQL, operation + '($v: String = "default") { alias: f(arg: [$v]) }', [v: null])

        then:
        calls == 0
        result.dataPresent
        result.data == null
        assertCoercionError(result, ['alias'])

        where:
        operation << ['query', 'mutation']
    }

    def "#kind fetcher cannot bypass argument coercion"() {
        given:
        int calls = 0
        def fetcher = kind == 'light' ? Mock(LightDataFetcher) : PropertyDataFetcher.fetching({ source -> calls++; 'unexpected' } as Function)
        if (kind == 'light') {
            fetcher.get(_, _, _) >> { calls++; 'unexpected' }
        }
        def graphQL = graphQLBuilder('String! = "fallback"', fetcher).build()
        def input = ExecutionInput.newExecutionInput('query($v: String) { alias: f(arg: $v) }')
                .variables([v: null]).root([f: 'unexpected']).build()

        when:
        def result = graphQL.execute(input)

        then:
        calls == 0
        result.data == [alias: null]
        assertCoercionError(result, ['alias'])

        where:
        kind << ['light', 'property']
    }

    def "mutation argument failure preserves serial execution and #resultType null propagation"() {
        given:
        def calls = []
        DataFetcher fetcher = { calls << 'invalid'; 'unexpected' }
        DataFetcher goodFetcher = { env -> calls << env.field.resultKey; 'good' }
        def graphQL = graphQLBuilder('[String!]!', fetcher, resultType, goodFetcher).build()

        when:
        def result = executeValid(graphQL, '''
            mutation($v: String = "default") {
                before: good
                invalid: f(arg: [$v])
                after: good
            }
        ''', [v: null])

        then:
        calls == expectedCalls
        result.data == expectedData
        assertCoercionError(result, ['invalid'])

        where:
        resultType | expectedCalls       | expectedData
        'String'   | ['before', 'after'] | [before: 'good', invalid: null, after: 'good']
        'String!'  | ['before']          | null
    }

    def "subscription argument coercion precedes source resolver with readsArguments=#readsArguments and variables=#variables"() {
        given:
        int calls = 0
        Publisher publisher = Stub(Publisher)
        DataFetcher fetcher = { env ->
            calls++
            if (readsArguments) {
                env.getArgument('arg')
            }
            publisher
        }
        def graphQL = graphQLBuilder('[String!]!', fetcher).build()

        when:
        def result = executeValid(graphQL, 'subscription($v: String = "default") { alias: f(arg: [$v]) }', variables)

        then:
        calls == (invalid ? 0 : 1)
        result.errors.size() == (invalid ? 1 : 0)
        invalid ? result.data == null : result.data instanceof Publisher
        result.errors*.path == (invalid ? [['alias']] : [])

        where:
        [readsArguments, variables] << [[false, true], [[v: null], [:], [v: 'valid']]].combinations()
        invalid = variables.containsKey('v') && variables.v == null
    }

    def "argument defaults and valid values are resolved before the fetcher with variables=#variables"() {
        given:
        def received = []
        DataFetcher fetcher = { env -> received << env.getArgument('arg'); 'resolved' }
        def graphQL = graphQLBuilder('String! = "fallback"', fetcher).build()

        when:
        def result = executeValid(graphQL, 'query($v: String) { f(arg: $v) }', variables)

        then:
        result.errors.empty
        result.data == [f: 'resolved']
        received == [expected]

        where:
        variables    | expected
        [:]          | 'fallback'
        [v: 'valid'] | 'valid'
    }

    def "unexecuted fields do not coerce arguments for #selection"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = graphQLBuilder('[String!]!', fetcher).build()

        when:
        def result = executeValid(graphQL, 'query($v: String = "default") { ' + selection + ' }', [v: null])

        then:
        calls == 0
        result.errors.empty
        result.data == expected

        where:
        selection                                    | expected
        'f(arg: [$v]) @skip(if: true)'                 | [:]
        'f(arg: [$v]) @include(if: false)'             | [:]
        'absent { f(arg: [$v]) }'                      | [absent: null]
    }

    def "argument errors in list elements use the response path and preserve siblings"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = graphQLBuilder('[String!]!', fetcher).build()

        when:
        def result = executeValid(graphQL, '''
            query($v: String = "default") { nodes { ...ChildFields } }
            fragment ChildFields on Child { alias: f(arg: [$v]) good }
        ''', [v: null])

        then:
        calls == 0
        result.data == [nodes: [[alias: null, good: 'good'], [alias: null, good: 'good']]]
        result.errors*.path == [['nodes', 0, 'alias'], ['nodes', 1, 'alias']]
        result.errors.every { it instanceof ExceptionWhileDataFetching && it.exception instanceof NonNullableValueCoercedAsNullException }
    }

    def "deferred argument errors are reported in the deferred result without invoking the resolver"() {
        given:
        int calls = 0
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = graphQLBuilder('[String!]!', fetcher).build()
        def input = ExecutionInput.newExecutionInput('''
            query($v: String = "default") {
                good
                ... @defer { alias: f(arg: [$v]) }
            }
        ''').variables([v: null]).graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()

        when:
        IncrementalExecutionResult result = graphQL.execute(input)

        then:
        result.errors.empty
        result.data == [good: 'good']
        result.hasNext

        when:
        def subscriber = new CapturingSubscriber()
        result.incrementalItemPublisher.subscribe(subscriber)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilTrue(subscriber.isDone())
        def patches = subscriber.events.collectMany { it.toSpecification().incremental ?: [] }

        then:
        calls == 0
        subscriber.throwable == null
        patches.size() == 1
        patches[0].data == [alias: null]
        patches[0].errors.size() == 1
        patches[0].errors[0].path == ['alias']
    }

    def "literal coercion precedes resolver invocation and repeated argument reads reuse it"() {
        given:
        def events = []
        Coercing coercing = Stub(Coercing) {
            parseLiteral(_, _, _, _) >> { events << 'coerce'; 'coerced' }
        }
        DataFetcher fetcher = { env ->
            events << 'resolve'
            assert env.getArgument('arg') == 'coerced'
            assert env.arguments == [arg: 'coerced']
            assert env.executionStepInfo.arguments == [arg: 'coerced']
            'resolved'
        }
        def graphQL = scalarGraphQLBuilder(coercing, fetcher)
                .preparsedDocumentProvider { input, parseAndValidate ->
                    def entry = parseAndValidate.apply(input)
                    events.clear()
                    CompletableFuture.completedFuture(entry)
                }.build()

        when:
        def result = graphQL.execute('{ f(arg: "value") }')

        then:
        result.errors.empty
        result.data == [f: 'resolved']
        events == ['coerce', 'resolve']
    }

    def "runtime scalar coercion failures prevent resolver invocation"() {
        given:
        boolean executing = false
        int calls = 0
        Coercing coercing = Stub(Coercing) {
            parseLiteral(_, _, _, _) >> {
                if (executing) {
                    throw new CoercingParseLiteralException('Cannot coerce this argument')
                }
                'valid during validation'
            }
        }
        DataFetcher fetcher = { calls++; 'unexpected' }
        def graphQL = scalarGraphQLBuilder(coercing, fetcher)
                .preparsedDocumentProvider { input, parseAndValidate ->
                    def entry = parseAndValidate.apply(input)
                    executing = true
                    CompletableFuture.completedFuture(entry)
                }.build()

        when:
        def result = graphQL.execute('{ f(arg: "value") }')

        then:
        calls == 0
        result.data == [f: null]
        result.errors.size() == 1
        result.errors[0] instanceof ExceptionWhileDataFetching
        result.errors[0].exception instanceof CoercingParseLiteralException
        result.errors[0].path == ['f']
    }

    def "coercion failures use the exception handler and complete fetch instrumentation"() {
        given:
        DataFetcher fetcher = Mock(DataFetcher)
        def fetchContext = Mock(FieldFetchingInstrumentationContext)
        def instrumentation = Spy(SimplePerformantInstrumentation) {
            beginFieldFetching(_, _) >> fetchContext
        }
        def handled = []
        def graphQL = graphQLBuilder('[String!]!', fetcher)
                .instrumentation(instrumentation)
                .defaultDataFetcherExceptionHandler { parameters ->
                    handled << parameters.exception
                    def error = GraphqlErrorBuilder.newError(parameters.dataFetchingEnvironment).message('handled coercion error').build()
                    CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build())
                }.build()

        when:
        def result = executeValid(graphQL, 'query($v: String = "default") { f(arg: [$v]) }', [v: null])

        then:
        0 * fetcher.get(_)
        1 * fetchContext.onDispatched()
        1 * fetchContext.onFetchedValue(_)
        1 * fetchContext.onExceptionHandled(_)
        1 * fetchContext.onCompleted(_, _)
        handled.size() == 1
        handled[0] instanceof NonNullableValueCoercedAsNullException
        result.data == [f: null]
        result.errors*.message == ['handled coercion error']
        result.errors*.path == [['f']]
    }

    def "argument failure does not prevent sibling DataLoader dispatch with mode=#mode"() {
        given:
        int calls = 0
        def batches = []
        BatchLoader<String, String> batchLoader = { keys -> batches << keys; CompletableFuture.completedFuture(keys) }
        def registry = DataLoaderRegistry.newRegistry().register('values', DataLoaderFactory.newDataLoader(batchLoader)).build()
        DataFetcher fetcher = { calls++; 'unexpected' }
        DataFetcher loaderFetcher = { env -> env.getDataLoader('values').load('loaded') }
        def graphQL = graphQLBuilder('[String!]!', fetcher, 'String', loaderFetcher).build()
        def input = ExecutionInput.newExecutionInput('query($v: String = "default") { f(arg: [$v]) good }')
                .variables([v: null]).dataLoaderRegistry(registry)
                .graphQLContext(mode == null ? [:] : [(mode): true]).build()

        when:
        def result = graphQL.executeAsync(input).get(10, TimeUnit.SECONDS)

        then:
        calls == 0
        result.data == [f: null, good: 'loaded']
        assertCoercionError(result, ['f'])
        batches == [['loaded']]

        where:
        mode << [null, ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING]
    }

    private GraphQL.Builder graphQLBuilder(String argumentType, DataFetcher fetcher, String resultType = 'String', DataFetcher goodFetcher = { 'good' } as DataFetcher) {
        def sdl = """
            input RequiredInput { a: String! }
            input ListInput { items: [String!] }
            type Child { f(arg: $argumentType): $resultType good: String }
            type Query { f(arg: $argumentType): $resultType good: String absent: Child nodes: [Child] }
            type Mutation { f(arg: $argumentType): $resultType good: String }
            type Subscription { f(arg: $argumentType): $resultType }
        """
        TestUtil.graphQL(sdl, [
                Query: [f: fetcher, good: goodFetcher, nodes: { [[:], [:]] } as DataFetcher],
                Mutation: [f: fetcher, good: goodFetcher], Subscription: [f: fetcher],
                Child: [f: fetcher, good: goodFetcher]
        ])
    }

    private GraphQL.Builder scalarGraphQLBuilder(Coercing coercing, DataFetcher fetcher) {
        def scalar = GraphQLScalarType.newScalar().name('Custom').coercing(coercing).build()
        def wiring = RuntimeWiring.newRuntimeWiring().scalar(scalar).type(newTypeWiring('Query').dataFetcher('f', fetcher)).build()
        TestUtil.graphQL('scalar Custom type Query { f(arg: Custom): String }', wiring)
    }

    private ExecutionResult executeValid(GraphQL graphQL, String query, Map<String, Object> variables) {
        def input = ExecutionInput.newExecutionInput(query).variables(variables).build()
        assert !ParseAndValidate.parseAndValidate(graphQL.graphQLSchema, input).isFailure()
        graphQL.execute(input)
    }

    private void assertCoercionError(ExecutionResult result, List<Object> path) {
        assert result.errors.size() == 1
        assert result.errors[0] instanceof ExceptionWhileDataFetching
        assert result.errors[0].exception instanceof NonNullableValueCoercedAsNullException
        assert result.errors[0].path == path
    }
}
