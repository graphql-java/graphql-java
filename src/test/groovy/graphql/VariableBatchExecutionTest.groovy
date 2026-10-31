package graphql

import graphql.execution.ExecutionId
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.DocumentAndVariables
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.extensions.ExtensionsBuilder
import graphql.incremental.IncrementalExecutionResult
import graphql.parser.Parser
import graphql.parser.ParserOptions
import graphql.parser.ParsingListener
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT

@Timeout(15)
class VariableBatchExecutionTest extends Specification {
    def schema = TestUtil.schema('type Query { value(id: Int = 7): Int! }',
            [Query: [value: { env -> env.getArgument('id') } as DataFetcher]])

    def "empty and singleton lists retain ordinary execution"() {
        when:
        def result = GraphQL.newGraphQL(schema).build().execute(ExecutionInput.newExecutionInput()
                .query('query($id:Int) { value(id:$id) }').variables(variables).build())

        then:
        !(result instanceof VariableBatchExecutionResult)
        result.toSpecification() == [data: [value: expected]]

        where:
        variables  | expected
        []         | 7
        [[:]]      | 7
        [[id: 3]]  | 3
    }

    def "legacy variables become a singleton and transforms preserve every map"() {
        given:
        def maps = [[id: 1], [id: null]]
        def input = ExecutionInput.newExecutionInput('{ value }').variables(maps).profileExecution(true).build()
        maps[0].id = 99
        maps.add([id: 3])

        expect:
        ExecutionInput.newExecutionInput('{ value }').variables([id: 1]).build().variablesList == [[id: 1]]
        input.variablesList == [[id: 1], [id: null]]
        input.transform { it.operationName('Op') }.variablesList == [[id: 1], [id: null]]
        input.transform { it.operationName('Op') }.profileExecution
        input.variables == [id: 1]

        when:
        input.variablesList[0].put('id', 4)

        then:
        thrown(UnsupportedOperationException)
    }

    def "separate variable coercion and conditional selection for every entry"() {
        when:
        def batch = batch(GraphQL.newGraphQL(schema).build(),
                'query($id: Int!,$include:Boolean! = true) { value(id:$id) @include(if:$include) }',
                [[id: 1], [id: 2, include: false], [id: 'bad'], [id: 1]])
        def results = batch.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        results.size() == 4
        results[0].data == [value: 1]
        results[1].data == [:]
        !results[2].dataPresent
        results[2].errors.size() == 1
        results[3].data == [value: 1]
    }

    def "common request errors are returned for every index"() {
        when:
        def results = batch(GraphQL.newGraphQL(schema).build(), query, [[:], [:]])
                .resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        results.size() == 2
        results.every { !it.errors.empty && !it.dataPresent }

        where:
        query << ['{', '{ missing }', 'query A { value } query B { value }', 'fragment F on Query { value }']
    }

    def "shared dataloaders combine entries and cache repeated keys"() {
        given:
        def calls = []
        def registry = new DataLoaderRegistry()
        registry.register('values', DataLoaderFactory.newDataLoader({ keys ->
            calls.add(new ArrayList(keys))
            CompletableFuture.completedFuture(keys)
        } as BatchLoader))
        def loadedSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getDataLoader('values').load(env.getArgument('id')) } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(loadedSchema).build()

        when:
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2], [id: 1]])
                .dataLoaderRegistry(registry).build()
        VariableBatchExecutionResult result = graphQL.execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        entries*.data == [[value: 1], [value: 2], [value: 1]]
        calls == [[1, 2]]
    }

    def "chained loaders share their registries and dispatch without hanging"() {
        given:
        def calls = []
        def registry = new DataLoaderRegistry()
        ['first', 'second'].each { name ->
            registry.register(name, DataLoaderFactory.newDataLoader({ keys ->
                calls.add([name, new ArrayList(keys)])
                CompletableFuture.completedFuture(keys)
            } as BatchLoader))
        }
        def loadedSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env ->
                    env.getDataLoader('first').load(env.getArgument('id')).thenCompose { value ->
                        env.getDataLoader('second').load(value)
                    }
                } as DataFetcher]])

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(loadedSchema).build().execute(
                ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                        .variables([[id: 1], [id: 2]]).dataLoaderRegistry(registry).build())
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        entries*.data == [[value: 1], [value: 2]]
        calls*.first().containsAll(['first', 'second'])
    }

    def "completion order streams immediately while collection preserves input order"() {
        given:
        def values = [new CompletableFuture<Integer>(), new CompletableFuture<Integer>()]
        def asyncSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> values[env.getArgument('id')] } as DataFetcher]])
        def emitted = []
        def result = batch(GraphQL.newGraphQL(asyncSchema).build(),
                'query($id:Int!) { value(id:$id) }', [[id: 0], [id: 1]])
        def completed = Flux.from(result.results).doOnNext { emitted.add(it) }.collectList().toFuture()

        when:
        values[1].complete(11)

        then:
        emitted*.variableIndex == [1]
        !completed.done

        when:
        values[0].complete(10)

        then:
        completed.get(5, TimeUnit.SECONDS)*.variableIndex == [1, 0]
    }

    def "non-null failure is confined to its execution"() {
        given:
        def failingSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getArgument('id') == 1 ? null : 2 } as DataFetcher]])

        when:
        def results = batch(GraphQL.newGraphQL(failingSchema).build(),
                'query($id:Int!) { value(id:$id) }', [[id: 1], [id: 2]]).resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        results[0].data == null
        results[0].errors[0].path == ['value']
        results[1].toSpecification() == [data: [value: 2]]
    }

    def "instrumentation sees individual inputs contexts and ids"() {
        given:
        def seen = []
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters,
                                                      InstrumentationState state) {
                seen.add(input)
                input.graphQLContext.put('entry', input.variableIndex)
                input.transform { it.variables([id: input.variables.id + 10]) }
            }
        }
        def graphQL = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build()
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2]]).executionId(ExecutionId.from('batch'))
                .graphQLContext([app: 'shared']).build()

        when:
        VariableBatchExecutionResult result = graphQL.execute(input)
        def results = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        results*.data == [[value: 11], [value: 12]]
        seen*.variableIndex == [0, 1]
        seen*.variableBatchId*.toString() == ['batch', 'batch']
        seen*.executionId.toSet().size() == 2
        !seen[0].graphQLContext.is(seen[1].graphQLContext)
        !input.graphQLContext.hasKey('entry')
        seen.every { it.graphQLContext.get('app') == 'shared' }
    }

    def "batch input cancellation stops pending work"() {
        given:
        def invoked = []
        def asyncSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env ->
                    invoked.add(env.getArgument('id'))
                    new CompletableFuture<Integer>()
                } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2], [id: 3]]).build()
        VariableBatchExecutionResult batch = GraphQL.newGraphQL(asyncSchema).maxVariableBatchConcurrency(1).build().execute(input)
        def result = batch.resultsAsync

        when:
        input.cancel()

        then:
        result.completedExceptionally
        invoked == [1]
    }

    def "subscription batches throw before producing a batch result"() {
        given:
        def invoked = false
        def subscriptionSchema = TestUtil.schema('type Query { value:Int } type Subscription { value:Int }',
                [Subscription: [value: { invoked = true } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput(query).operationName(operationName).variables([[:], [:]]).build()

        when:
        GraphQL.newGraphQL(subscriptionSchema).build().execute(input)

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Variable batching does not support subscription operations; supply at most one variable map.'
        !invoked

        where:
        query                                             | operationName
        'subscription { value }'                          | null
        '# comment\nsubscription S { value }'              | 'S'
        'subscription { missing }'                        | null
        'query Q { value } subscription S { value }'       | 'S'
    }

    def "async subscription batches fail before instrumentation or variable coercion"() {
        given:
        def instrumentation = Mock(graphql.execution.instrumentation.Instrumentation)
        def input = ExecutionInput.newExecutionInput('subscription($id:Int!) { value(id:$id) }')
                .variables([[id: 'bad'], [:]]).build()

        when:
        def result = GraphQL.newGraphQL(schema).instrumentation(instrumentation).build().executeAsync(input)
        result.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause instanceof IllegalArgumentException
        error.cause.message.contains('subscription operations')
        0 * instrumentation._
    }

    def "empty and singleton variable lists still allow ordinary subscriptions"() {
        given:
        def subscriptionSchema = TestUtil.schema('type Query { value:Int } type Subscription { value:Int }',
                [Subscription: [value: { Flux.just(7) } as DataFetcher]])

        when:
        def result = GraphQL.newGraphQL(subscriptionSchema).build().execute(
                ExecutionInput.newExecutionInput('subscription { value }').variables(variables).build())
        def events = Flux.from(result.data).collectList().block(Duration.ofSeconds(5))

        then:
        !(result instanceof VariableBatchExecutionResult)
        events*.data == [[value: 7]]

        where:
        variables << [[], [[:]]]
    }

    def "a query can be selected from a document that also contains a subscription"() {
        given:
        def subscriptionSchema = TestUtil.schema('type Query { value:Int } type Subscription { value:Int }',
                [Query: [value: { 7 } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query Q { value } subscription S { value }')
                .operationName('Q').variables([[:], [:]]).build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(subscriptionSchema).build().execute(input)

        then:
        result.resultsAsync.get(5, TimeUnit.SECONDS)*.toSpecification() == [[data: [value: 7]], [data: [value: 7]]]
    }

    def "cached persisted subscriptions fail the whole batch before any resolver runs"() {
        given:
        def invoked = false
        def subscriptionSchema = TestUtil.schema('type Query { value:Int } type Subscription { value:Int }',
                [Subscription: [value: { invoked = true; Flux.just(7) } as DataFetcher]])
        def provider = { input, compute -> CompletableFuture.completedFuture(
                new PreparsedDocumentEntry(Parser.parse('subscription { value }'))) } as PreparsedDocumentProvider
        def input = ExecutionInput.newExecutionInput(ExecutionInput.PERSISTED_QUERY_MARKER).variables([[:], [:]]).build()
        VariableBatchExecutionResult result = GraphQL.newGraphQL(subscriptionSchema).preparsedDocumentProvider(provider).build().execute(input)

        when:
        result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause instanceof IllegalArgumentException
        error.cause.message.contains('subscription operations')
        !invoked
    }

    def "document instrumentation cannot introduce a batched subscription"() {
        given:
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables value,
                    InstrumentationExecutionParameters parameters, InstrumentationState state) {
                value.transform { it.document(Parser.parse('subscription { missing }')) }
            }
        }

        when:
        batch(GraphQL.newGraphQL(schema).instrumentation(instrumentation).build(), '{ value }', [[:], [:]])
                .resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause instanceof IllegalArgumentException
        error.cause.message.contains('subscription operations')
    }

    def "replacing an execution input cannot bypass the subscription batch restriction"() {
        given:
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters,
                                                      InstrumentationState state) {
                ExecutionInput.newExecutionInput('subscription { value }').build()
            }
        }

        when:
        batch(GraphQL.newGraphQL(schema).instrumentation(instrumentation).build(), '{ value }', [[:], [:]])
                .resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause instanceof IllegalArgumentException
        error.cause.message.contains('subscription operations')
    }

    def "batch limits are checked before fetching and survive GraphQL transform"() {
        given:
        def graphQL = GraphQL.newGraphQL(schema).maxVariableBatchSize(2).maxVariableBatchConcurrency(1)
                .build().transform { it.valueUnboxer(graphql.execution.ValueUnboxer.DEFAULT) }

        when:
        graphQL.execute(ExecutionInput.newExecutionInput('{ value }').variables([[:], [:], [:]]).build())

        then:
        def error = thrown(graphql.execution.AbortExecutionException)
        error.message.contains('exceeds maximum allowed 2')

        when:
        GraphQL.newGraphQL(schema).maxVariableBatchSize(0)

        then:
        thrown(AssertException)

        when:
        GraphQL.newGraphQL(schema).maxVariableBatchConcurrency(0)

        then:
        thrown(AssertException)
    }

    def "subscriber demand bounds scheduling and cancellation stops new executions"() {
        given:
        def calls = []
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> calls.add(env.getArgument('id')); env.getArgument('id') } as DataFetcher]])
        def result = batch(GraphQL.newGraphQL(localSchema).build(), 'query($id:Int!) { value(id:$id) }',
                [[id: 1], [id: 2], [id: 3]])

        when:
        def first = Flux.from(result.results).limitRate(1).take(1).collectList().block(Duration.ofSeconds(5))

        then:
        first*.variableIndex == [0]
        calls == [1]
    }

    def "collected results keep input order across asynchronous completion"() {
        given:
        def values = [new CompletableFuture<Integer>(), new CompletableFuture<Integer>()]
        def asyncSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> values[env.getArgument('id')] } as DataFetcher]])
        def result = batch(GraphQL.newGraphQL(asyncSchema).build(), 'query($id:Int!) { value(id:$id) }',
                [[id: 0], [id: 1]]).resultsAsync

        when:
        values[1].complete(11)
        values[0].complete(10)

        then:
        result.get(5, TimeUnit.SECONDS)*.data == [[value: 10], [value: 11]]
    }

    def "mutation fields stay serial and cache invalidation applies to the shared registry"() {
        given:
        def values = [key: 0]
        def calls = []
        def registry = new DataLoaderRegistry()
        def loader = DataLoaderFactory.newDataLoader({ keys ->
            calls.add(new ArrayList(keys))
            CompletableFuture.completedFuture(keys.collect { values[it] })
        } as BatchLoader)
        registry.register('values', loader)
        def mutationSchema = TestUtil.schema('''
            type Query { value:Int }
            type Mutation { update(value:Int!):Int! read:Int! }
        ''', [Mutation: [update: { env ->
            values.key = env.getArgument('value')
            env.getDataLoader('values').clear('key').prime('key', values.key)
            values.key
        } as DataFetcher, read: { env -> env.getDataLoader('values').load('key') } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('mutation($value:Int!) { update(value:$value) read }')
                .variables([[value: 1], [value: 2]]).dataLoaderRegistry(registry).build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(mutationSchema)
                .maxVariableBatchConcurrency(1).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        entries*.data == [[update: 1, read: 1], [update: 2, read: 2]]
        calls.empty
        values.key == 2
    }

    def "extension builders are isolated including a supplied builder"() {
        given:
        def extensions = ExtensionsBuilder.newExtensionsBuilder().addValue('initial', true)
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env ->
                    env.graphQlContext.get(ExtensionsBuilder).addValue('entry', env.getArgument('id'))
                    env.getArgument('id')
                } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2]]).graphQLContext([(ExtensionsBuilder): extensions]).context('legacy').build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        entries*.extensions == [[initial: true, entry: 1], [initial: true, entry: 2]]
        extensions.buildExtensions() == [initial: true]
    }

    def "custom document instrumentation and preparsed providers retain per-entry behavior"() {
        given:
        def seen = []
        def provider = { ExecutionInput input, compute ->
            seen.add(input.variables.id)
            CompletableFuture.completedFuture(compute.apply(input))
        } as PreparsedDocumentProvider
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables value,
                    InstrumentationExecutionParameters parameters, InstrumentationState state) {
                value.transform { it.variables([id: parameters.variables.id + 10]) }
            }
        }

        when:
        def result = batch(GraphQL.newGraphQL(schema).preparsedDocumentProvider(provider)
                .instrumentation(instrumentation).build(), 'query($id:Int!) { value(id:$id) }', [[id: 1], [id: 2]])
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        seen == [1, 2]
        entries*.data == [[value: 11], [value: 12]]
    }

    def "default batch preparation runs eagerly and is reused by every entry"() {
        given:
        def calls = new AtomicInteger()
        def listener = { token -> if (token.text == 'query') calls.incrementAndGet() } as ParsingListener
        def options = ParserOptions.newParserOptions().parsingListener(listener).build()
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2]]).graphQLContext([(ParserOptions): options]).build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(schema).build().execute(input)

        then:
        calls.get() == 1

        when:
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        entries*.data == [[value: 1], [value: 2]]
        calls.get() == 1
    }

    def "incremental execution remains available per variable entry"() {
        given:
        def registry = new DataLoaderRegistry()
        registry.register('values', DataLoaderFactory.newDataLoader({ keys -> CompletableFuture.completedFuture(keys) } as BatchLoader))
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getDataLoader('values').load(env.getArgument('id')) } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { ... @defer { value(id:$id) } }')
                .variables([[id: 1], [id: 2]]).dataLoaderRegistry(registry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)
        def payloads = entries.collect { IncrementalExecutionResult entry ->
            Flux.from(entry.incrementalItemPublisher).collectList().block(Duration.ofSeconds(5))
        }

        then:
        entries.every { it instanceof IncrementalExecutionResult }
        payloads.collect { it[0].incremental[0].data } == [[value: 1], [value: 2]]
    }

    def "an asynchronous shared loader failure belongs to each affected entry"() {
        given:
        def pending = new CompletableFuture<List<Integer>>()
        def keysSeen = []
        def registry = new DataLoaderRegistry().register('values', DataLoaderFactory.newDataLoader({ keys ->
            keysSeen.add(new ArrayList(keys))
            pending
        } as BatchLoader))
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getDataLoader('values').load(env.getArgument('id')) } as DataFetcher]])
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(
                ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                        .variables([[id: 1], [id: 2]]).dataLoaderRegistry(registry).build())
        def entries = result.resultsAsync

        when:
        pending.completeExceptionally(new IllegalStateException('backend unavailable'))

        then:
        entries.get(5, TimeUnit.SECONDS).every { it.data == null && it.errors[0].path == ['value'] }
        keysSeen == [[1, 2]]
    }

    def "cancelling an individual execution does not cancel a shared loader future"() {
        given:
        def seen = []
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters,
                                                      InstrumentationState state) {
                seen.add(input)
                input
            }
        }
        def pending = new CompletableFuture<List<Integer>>()
        def registry = new DataLoaderRegistry().register('values', DataLoaderFactory.newDataLoader({ keys -> pending } as BatchLoader))
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getDataLoader('values').load(env.getArgument('id')) } as DataFetcher]])
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).instrumentation(instrumentation).build().execute(
                ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                        .variables([[id: 1], [id: 1]]).dataLoaderRegistry(registry).build())
        def entries = result.resultsAsync

        when:
        seen[0].cancel()
        pending.complete([10])

        then:
        !pending.cancelled
        !entries.get(5, TimeUnit.SECONDS)[0].errors.empty
        entries.get(5, TimeUnit.SECONDS)[1].toSpecification() == [data: [value: 10]]
    }

    def "pre-cancelled input and cancelled collection do not start queued entries"() {
        given:
        def calls = []
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> calls.add(env.getArgument('id')); new CompletableFuture<Integer>() } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { value(id:$id) }')
                .variables([[id: 1], [id: 2]]).build()
        if (beforeSubscription) input.cancel()
        VariableBatchExecutionResult batch = GraphQL.newGraphQL(localSchema).maxVariableBatchConcurrency(1).build().execute(input)

        when:
        def result = batch.resultsAsync
        if (!beforeSubscription) result.cancel(false)

        then:
        result.completedExceptionally
        calls == (beforeSubscription ? [] : [1])

        where:
        beforeSubscription << [true, false]
    }

    def "batch cancellation still reaches deferred executions after initial results complete"() {
        given:
        def called = []
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> called.add(env.getArgument('id')); env.getArgument('id') } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { ... @defer { value(id:$id) } }')
                .variables([[id: 1], [id: 2]]).graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)

        when:
        input.cancel()
        Flux.from((entries[0] as IncrementalExecutionResult).incrementalItemPublisher)
                .collectList().block(Duration.ofSeconds(5))

        then:
        thrown(java.util.concurrent.CancellationException)
        called.empty
    }

    def "publisher rejects nonpositive demand and repeated subscriptions"() {
        given:
        def result = batch(GraphQL.newGraphQL(schema).build(), '{ value }', [[:], [:]])
        def errors = []
        def subscriber = [onSubscribe: { Subscription subscription -> subscription.request(0) },
                          onNext: { throw new AssertionError('unexpected result') },
                          onError: { errors.add(it) }, onComplete: {}] as Subscriber

        when:
        result.results.subscribe(subscriber)
        result.results.subscribe(subscriber)

        then:
        errors*.class == [IllegalArgumentException, IllegalStateException]
        result.data.is(result.results)
        result.size == 2

        when:
        result.toSpecification()

        then:
        thrown(UnsupportedOperationException)
    }

    def "unexpected instrumentation failures fail the batch without starting pending entries"() {
        given:
        def seen = []
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters,
                                                      InstrumentationState state) {
                seen.add(input.variableIndex)
                throw new IllegalStateException('broken instrumentation')
            }
        }

        when:
        batch(GraphQL.newGraphQL(schema).instrumentation(instrumentation).maxVariableBatchConcurrency(1).build(),
                '{ value }', [[:], [:]]).resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def failure = thrown(java.util.concurrent.ExecutionException)
        failure.cause.message == 'broken instrumentation'
        seen == [0]
    }

    def "concurrency bounds active work without waiting for a slow earlier entry"() {
        given:
        def called = []
        def pending = (0..3).collect { new CompletableFuture<Integer>() }
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env ->
                    def id = env.getArgument('id')
                    called.add(id)
                    pending[id]
                } as DataFetcher]])
        def result = batch(GraphQL.newGraphQL(localSchema).maxVariableBatchConcurrency(2).build(),
                'query($id:Int!) { value(id:$id) }', (0..3).collect { [id: it] }).resultsAsync

        expect:
        called == [0, 1]

        when:
        pending[1].complete(1)

        then:
        called == [0, 1, 2]

        when:
        pending[2].complete(2)
        pending[3].complete(3)
        pending[0].complete(0)

        then:
        result.get(5, TimeUnit.SECONDS)*.data == [[value: 0], [value: 1], [value: 2], [value: 3]]
    }

    def "persisted query lookup can replace the query while retaining every variables map"() {
        given:
        def seen = []
        def provider = { ExecutionInput input, compute ->
            seen.add(input.extensions.id)
            CompletableFuture.completedFuture(compute.apply(input.transform {
                it.query('query($id:Int!) { value(id:$id) }')
            }))
        } as PreparsedDocumentProvider
        def input = ExecutionInput.newExecutionInput(graphql.execution.preparsed.persisted.PersistedQuerySupport.PERSISTED_QUERY_MARKER)
                .extensions([id: 'known-query']).variables([[id: 1], [id: 2]]).build()

        when:
        VariableBatchExecutionResult result = GraphQL.newGraphQL(schema).preparsedDocumentProvider(provider).build().execute(input)

        then:
        result.resultsAsync.get(5, TimeUnit.SECONDS)*.data == [[value: 1], [value: 2]]
        seen == ['known-query', 'known-query']
    }

    def "cancelling a deferred subscription leaves other entries available"() {
        given:
        def pending = new CompletableFuture<Integer>()
        def localSchema = TestUtil.schema('type Query { value(id:Int!):Int! }',
                [Query: [value: { env -> env.getArgument('id') == 1 ? pending : 2 } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('query($id:Int!) { ... @defer { value(id:$id) } }')
                .variables([[id: 1], [id: 2]]).graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)
        def first = Flux.from((entries[0] as IncrementalExecutionResult).incrementalItemPublisher).collectList().toFuture()

        when:
        first.cancel(false)
        pending.complete(1)
        def second = Flux.from((entries[1] as IncrementalExecutionResult).incrementalItemPublisher)
                .collectList().block(Duration.ofSeconds(5))

        then:
        first.cancelled
        !pending.cancelled
        second[0].incremental[0].data == [value: 2]
    }

    def "input cancellation terminates an already subscribed deferred publisher"() {
        given:
        def localSchema = TestUtil.schema('type Query { value:Int! }',
                [Query: [value: { new CompletableFuture<Integer>() } as DataFetcher]])
        def input = ExecutionInput.newExecutionInput('{ ... @defer { value } }')
                .variables([[:], [:]]).graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true]).build()
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(input)
        def entries = result.resultsAsync.get(5, TimeUnit.SECONDS)
        def deferred = Flux.from((entries[0] as IncrementalExecutionResult).incrementalItemPublisher).collectList().toFuture()

        when:
        input.cancel()

        then:
        deferred.completedExceptionally
    }

    def "variable lists reject a null list or a null entry"() {
        when:
        ExecutionInput.newExecutionInput('{ value }').variables((List<Map<String, Object>>) values)

        then:
        thrown(AssertException)

        where:
        values << [null, [[id: 1], null]]
    }

    def "instrumentation cannot replace the shared dataloader registry per entry"() {
        given:
        def instrumentation = new SimplePerformantInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput input, InstrumentationExecutionParameters parameters,
                                                      InstrumentationState state) {
                input.transform { it.dataLoaderRegistry(new DataLoaderRegistry()) }
            }
        }

        when:
        batch(GraphQL.newGraphQL(schema).instrumentation(instrumentation).build(), '{ value }', [[:], [:]])
                .resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause.message.contains('must share their DataLoader registry')
    }

    def "unexpected registry dispatch failure terminates the batch"() {
        given:
        def registry = Stub(DataLoaderRegistry) {
            getDataLoaders() >> { throw new IllegalStateException('broken registry') }
        }
        def localSchema = TestUtil.schema('type Query { value:Int }',
                [Query: [value: { new CompletableFuture<Integer>() } as DataFetcher]])
        VariableBatchExecutionResult result = GraphQL.newGraphQL(localSchema).build().execute(
                ExecutionInput.newExecutionInput('{ value }').variables([[:], [:]]).dataLoaderRegistry(registry).build())

        when:
        result.resultsAsync.get(5, TimeUnit.SECONDS)

        then:
        def error = thrown(java.util.concurrent.ExecutionException)
        error.cause.message == 'broken registry'
    }

    private static VariableBatchExecutionResult batch(GraphQL graphQL, String query, List<Map<String, Object>> variables) {
        graphQL.execute(ExecutionInput.newExecutionInput(query).variables(variables).build()) as VariableBatchExecutionResult
    }
}
