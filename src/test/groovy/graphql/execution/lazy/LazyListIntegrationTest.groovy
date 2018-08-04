package graphql.execution.lazy

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.defer.BasicSubscriber
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class LazyListIntegrationTest extends Specification {
    def schemaSpec = '''
            type Query {
                items: [Item!]
            }
            
            type Item {
                field: Int!
                exception: Int
                items: [Item]
            }
        '''

    def query = '''
            query {
                items {
                    field
                    items {
                        field
                    }
                }
            }
        '''

    def complex = false
    def flushOn = null
    def fieldNullOn = null
    def fetchedValue = new AtomicInteger()
    def lazyListsFetched = new AtomicInteger();

    def itemsFetcher = new DataFetcher() {
        @Override
        LazyList get(DataFetchingEnvironment environment) {
            lazyListsFetched.incrementAndGet();
            def stream = IntStream.range(0, 3).mapToObj { i -> [i, fetchedValue.incrementAndGet()] }
            return { action ->
                stream.forEach({ orig, fetched ->
                    def flush = flushOn == null || flushOn == orig
                    action.accept(flush, fetched)
                })
            }
        }
    }

    def fieldFetcher = new DataFetcher() {
        @Override
        Integer get(DataFetchingEnvironment environment) {
            if (fieldNullOn == environment.source) {
                null
            } else {
                environment.source
            }
        }
    }

    def exceptionFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            throw new RuntimeException("dummy")
        }
    }

    GraphQL graphQL = null

    def setup() {
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("items", itemsFetcher))
                .type(newTypeWiring("Item")
                .dataFetcher("field", fieldFetcher)
                .dataFetcher("items", itemsFetcher)
                .dataFetcher("exception", exceptionFetcher)
        )
                .build()
        def schema = TestUtil.schema(schemaSpec, runtimeWiring)
        graphQL = GraphQL.newGraphQL(schema).build()
    }

    def collectResults(def executionResult) {
        def results = []
        def items = executionResult.data
        if (items instanceof Map) {
            items = items.items
        }
        if (!items) {
            return null
        }
        def fetch = { item ->
            def outerResult = complex ? [item?.field, fetchedValue.get()] : item?.field
            def innerResults = null
            if (item && item.items) {
                innerResults = []
                item.items.forEach({ innerFlush, innerItem ->
                    innerResults << (complex ? [innerItem?.field, fetchedValue.get()] : innerItem?.field)
                })
            }
            results << [outerResult, innerResults]
        }
        if (!(items instanceof LazyList)) {
            fetch(items)
        } else {
            items.forEach({ flush, item -> fetch(item) })
        }
        results
    }

    def "test lazy list simple"() {
        complex = true

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [
                [[1, 1], [
                        [2, 2],
                        [3, 3],
                        [4, 4],
                ]],
                [[5, 5], [
                        [6, 6],
                        [7, 7],
                        [8, 8],
                ]],
                [[9, 9], [
                        [10, 10],
                        [11, 11],
                        [12, 12],
                ]],
        ]
    }

    def "test lazy list non-blocking"() {
        flushOn = 1
        complex = true

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [
                [[1, 2], [
                        [3, 4],
                        [4, 4],
                        [5, 5],
                ]],
                [[2, 5], [
                        [6, 7],
                        [7, 7],
                        [8, 8],
                ]],
                [[9, 9], [
                        [10, 11],
                        [11, 11],
                        [12, 12],
                ]],
        ]
    }

    def "test lazy list with inner null field"() {
        fieldNullOn = 3
        complex = true

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [
                [[1, 1], [
                        [2, 2],
                        [null, 3],
                        [4, 4],
                ]],
                [[5, 5], [
                        [6, 6],
                        [7, 7],
                        [8, 8],
                ]],
                [[9, 9], [
                        [10, 10],
                        [11, 11],
                        [12, 12],
                ]],
        ]
        executionResult.errors*.path == [
                ["items", 0, "items", 1, "field"],
        ]
    }

    def "test lazy list with outer null field"() {
        fieldNullOn = 5

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [[1, [2, 3, 4]]]
        executionResult.errors*.path == [
                ["items", 1, "field"],
                ["items"]
        ]
    }

    def "test lazy list with inner defer"() {
        given:
        def query = '''
            query {
                items {
                    field
                    items @defer {
                        field
                    }
                }
            }
        '''

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.extensions == null
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [
                [1, null],
                [2, null],
                [3, null],
        ]
        executionResult.extensions != null

        when:
        def subResults = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult subExecutionResult) {
                subResults << subExecutionResult
                subscription.request(1)
            }
        }
        executionResult.extensions.get(GraphQL.DEFERRED_RESULTS).subscribe(subscriber)

        then:
        collectResults(subResults[0]) == [
                [4, null],
                [5, null],
                [6, null],
        ]
        collectResults(subResults[1]) == [
                [7, null],
                [8, null],
                [9, null],
        ]
        collectResults(subResults[2]) == [
                [10, null],
                [11, null],
                [12, null],
        ]
    }

    def "test lazy list with outer defer"() {
        given:
        def query = '''
            query {
                items @defer {
                    field
                    items {
                        field
                    }
                }
            }
        '''

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == null
        executionResult.extensions != null

        when:
        def subResults = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult subExecutionResult) {
                subResults << subExecutionResult
                subscription.request(1)
            }
        }
        executionResult.extensions.get(GraphQL.DEFERRED_RESULTS).subscribe(subscriber)

        then:
        subResults.size() == 1
        collectResults(subResults[0]) == [
                [1, [2, 3, 4]],
                [5, [6, 7, 8]],
                [9, [10, 11, 12]]
        ]
    }

    def "test lazy list with inner and outer defer"() {
        given:
        def query = '''
            query {
                items @defer {
                    field
                    items @defer {
                        field
                    }
                }
            }
        '''

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == null
        executionResult.extensions != null

        when:
        def subResults = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult subExecutionResult) {
                subResults << subExecutionResult
                subscription.request(1)
            }
        }
        executionResult.extensions.get(GraphQL.DEFERRED_RESULTS).subscribe(subscriber)

        then:
        subResults.size() == 1
        collectResults(subResults[0]) == [
                [1, null],
                [2, null],
                [3, null],
        ]
        subResults.size() == 4
        collectResults(subResults[1]) == [
                [4, null],
                [5, null],
                [6, null],
        ]
        collectResults(subResults[2]) == [
                [7, null],
                [8, null],
                [9, null],
        ]
        collectResults(subResults[3]) == [
                [10, null],
                [11, null],
                [12, null],
        ]
    }

    def "test lazy list with inner defer and exceptions"() {
        given:
        schemaSpec = '''
            type Query {
                items: [Item!]
            }
            
            type Item {
                field: Int!
                exception: Int
                items: [Item]
            }
        '''

        setup()

        def query = '''
            query {
                items {
                    field
                    exception
                    items @defer {
                        field
                        exception
                    }
                }
            }
        '''

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == [ [1, null], [2, null], [3, null] ]
        executionResult.errors*.path == [
                [ "items", 0, "exception" ],
                [ "items", 1, "exception" ],
                [ "items", 2, "exception" ],
        ]
        executionResult.extensions != null

        when:
        List<ExecutionResult> subResults = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult subExecutionResult) {
                subResults << subExecutionResult
                subscription.request(1)
            }
        }
        executionResult.extensions.get(GraphQL.DEFERRED_RESULTS).subscribe(subscriber)

        then:
        subResults.size() == 3
        subResults[0].errors.isEmpty()
        subResults[1].errors.isEmpty()
        subResults[2].errors.isEmpty()
        collectResults(subResults[0]) == [
                [4, null],
                [5, null],
                [6, null],
        ]
        subResults[0].errors*.path == [
                [ "items", 0, "items", 0, "exception" ],
                [ "items", 0, "items", 1, "exception" ],
                [ "items", 0, "items", 2, "exception" ],
        ]
        collectResults(subResults[1]) == [
                [7, null],
                [8, null],
                [9, null],
        ]
        subResults[1].errors*.path == [
                [ "items", 1, "items", 0, "exception" ],
                [ "items", 1, "items", 1, "exception" ],
                [ "items", 1, "items", 2, "exception" ],
        ]
        collectResults(subResults[2]) == [
                [10, null],
                [11, null],
                [12, null],
        ]
        subResults[2].errors*.path == [
                [ "items", 2, "items", 0, "exception" ],
                [ "items", 2, "items", 1, "exception" ],
                [ "items", 2, "items", 2, "exception" ],
        ]

        subResults*.extensions == [ null, null, null ]

        executionResult.errors*.path == [
                [ "items", 0, "exception" ],
                [ "items", 1, "exception" ],
                [ "items", 2, "exception" ],
        ]
    }

    def "test lazy list cancellation"() {
        given:
        def query = '''
            query {
                items {
                    items {
                        field
                    }
                    field
                }
            }
        '''
        fieldNullOn = 1

        when:
        def executionResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        executionResult.errors.isEmpty()
        collectResults(executionResult) == []
        lazyListsFetched.get() == 2
        !executionResult.deferSupport.lazySupport.hasPendingCompletions()
    }
}
