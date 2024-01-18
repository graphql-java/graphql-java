package graphql.execution.incremental

import graphql.DeferredExecutionResult
import graphql.Directives
import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.defer.CapturingSubscriber
import graphql.incremental.DelayedIncrementalExecutionResult
import graphql.incremental.IncrementalExecutionResult
import graphql.incremental.IncrementalExecutionResultImpl
import graphql.schema.DataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DeferSupportIntegrationTest extends Specification {
    def schemaSpec = '''
            type Query {
                post : Post 
            }
            
            type Post {
                id: ID!
                # takes 100ms to resolve
                summary : String
                # takes 300ms to resolve
                text : String
            }
        '''

    GraphQL graphQL = null

    private static DataFetcher resolve(Object value) {
        return (env) -> value
    }

    private static DataFetcher resolve(Object value, Integer sleepMs) {
        return (env) -> CompletableFuture.supplyAsync {
            println("" + new Date().getTime() + "|calling df for: " + value + ". Sleeping: " + sleepMs + ". Thread name: " + Thread.currentThread().name)
            Thread.sleep(sleepMs)
            println("" + new Date().getTime() + "|value resolved: " + value)
            return value
        }
    }

    void setup() {
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("post", resolve([id: "1001"])))
                .type(newTypeWiring("Post").dataFetcher("summary", resolve("A summary", 10)))
                .type(newTypeWiring("Post").dataFetcher("text", resolve("The full text", 1000)))
                .build()

        def schema = TestUtil.schema(schemaSpec, runtimeWiring)
                .transform({ builder -> builder.additionalDirective(Directives.DeferDirective) })
        this.graphQL = GraphQL.newGraphQL(schema).build()
    }

    def "simple defer"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer {
                        summary
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [id: "1001"]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        incrementalResults == [
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [summary: "A summary"]
                                ]
                        ]
                ]
        ]
    }

    def "simple defer with label"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer(label: "summary-defer") {
                        summary
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [id: "1001"]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        incrementalResults == [
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path : ["post"],
                                        label: "summary-defer",
                                        data : [summary: "A summary"]
                                ]
                        ]
                ]
        ]
    }

    @Unroll
    def "defer with 'if: #ifValue' "() {
        def query = """
            query {
                post {
                    id
                    ... @defer(if: $ifValue) {
                        summary
                    }
                }
            }
        """

        when:
        ExecutionResult executionResult = executeQuery(query)

        then:
        if (ifValue) {
            assert executionResult instanceof IncrementalExecutionResultImpl

            assert executionResult.toSpecification() == [
                    data   : [post: [id: "1001"]],
                    hasNext: true
            ]
            def incrementalResults = getIncrementalResults(executionResult)

            assert incrementalResults == [
                    [
                            hasNext    : false,
                            incremental: [
                                    [
                                            path: ["post"],
                                            data: [summary: "A summary"]
                                    ]
                            ]
                    ]
            ]
        } else {
            assert !(executionResult instanceof IncrementalExecutionResult)

            assert executionResult.toSpecification() == [
                    data: [post: [id: "1001", summary: "A summary"]],
            ]
        }

        where:
        ifValue << [true, false]
    }

    @Unroll
    def "defer with 'if: #ifValue' passed as variable "() {
        def query = """
            query(\$ifVar: Boolean!) {
                post {
                    id
                    ... @defer(if: \$ifVar) {
                        summary
                    }
                }
            }
        """

        when:
        ExecutionResult executionResult = executeQuery(query, [ifVar: ifValue])

        then:
        if (ifValue) {
            assert executionResult instanceof IncrementalExecutionResultImpl

            assert executionResult.toSpecification() == [
                    data   : [post: [id: "1001"]],
                    hasNext: true
            ]
            def incrementalResults = getIncrementalResults(executionResult)

            assert incrementalResults == [
                    [
                            hasNext    : false,
                            incremental: [
                                    [
                                            path: ["post"],
                                            data: [summary: "A summary"]
                                    ]
                            ]
                    ]
            ]
        } else {
            assert !(executionResult instanceof IncrementalExecutionResult)

            assert executionResult.toSpecification() == [
                    data: [post: [id: "1001", summary: "A summary"]],
            ]
        }

        where:
        ifValue << [true, false]
    }

    def "2 fields deferred together"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer {
                        summary
                        text
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [id: "1001"]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        incrementalResults == [
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [summary: "A summary", text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "2 fields deferred independently"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer(label: "summary-defer") {
                        summary
                    }
                    ... @defer(label: "text-defer") {
                        text
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [id: "1001"]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:

        incrementalResults == [
                [
                        hasNext    : true,
                        incremental: [
                                [
                                        label: "summary-defer",
                                        path: ["post"],
                                        data: [summary: "A summary"]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        label: "text-defer",
                                        path: ["post"],
                                        data: [text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "multiple defers on same field"() {

        def query = '''
            query {
                post {
                    sentAt
                    ... @defer {
                        postText
                    }
                    ... @defer(label: "defer-outer") {
                        postText
                        ... @defer(label: "defer-inner") {
                            postText
                        }
                    }
                }
            }
        '''

        when:
        def initialResult = executeQuery(query)

        then:
        initialResult.errors.isEmpty()
        initialResult.data == ["post": ["postText": "post_data"]]

        throw new IllegalStateException("Need to assert data fetcher for field is called just once")

//        when:
//
//        Publisher<DeferredExecutionResult> deferredResultStream = initialResult.extensions[GraphQL.DEFERRED_RESULTS] as Publisher<DeferredExecutionResult>
//
//        def subscriber = new CapturingSubscriber()
//        subscriber.subscribeTo(deferredResultStream)
//        Awaitility.await().untilTrue(subscriber.finished)
//
//        List<ExecutionResult> resultList = subscriber.executionResults
//
//        then:
//
//        assertDeferredData(resultList)
    }

    def "test defer support end to end"() {

        def query = '''
            query {
                post {
                    postText
                   
                    ... @defer {
                        a :comments(sleepTime:200) {
                            commentText
                        }
                    } 
                   
                    ... @defer {
                        b : reviews(sleepTime:100) {
                            reviewText
                            ... @defer {
                                comments(prefix : "b_") {
                                    commentText
                                }
                            }
                        }
                    } 

                    ... @defer {
                        c: reviews {
                            goes {
                                bang
                            }
                        }
                    }
                }
            }
        '''

        when:
        def initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        initialResult.errors.isEmpty()
        initialResult.data == ["post": ["postText": "post_data", a: null, b: null, c: null]]

        when:

        Publisher<DeferredExecutionResult> deferredResultStream = initialResult.extensions[GraphQL.DEFERRED_RESULTS] as Publisher<DeferredExecutionResult>

        def subscriber = new CapturingSubscriber()
        subscriber.subscribeTo(deferredResultStream)
        Awaitility.await().untilTrue(subscriber.finished)

        List<ExecutionResult> resultList = subscriber.executionResults

        then:

        assertDeferredData(resultList)
    }

    def "test defer support keeps the fields named correctly when interspersed in the query"() {

        def query = '''
            query {
                post {
                    interspersedA: echo(text:"before a:")
                    
                    a: comments(sleepTime:200) @defer {
                        commentText
                    }
                    
                    interspersedB: echo(text:"before b:")
                    
                    b : reviews(sleepTime:100) @defer {
                        reviewText
                        comments(prefix : "b_") @defer {
                            commentText
                        }
                    }

                    interspersedC: echo(text:"before c:")

                    c: reviews @defer {
                        goes {
                            bang
                        }
                    }
                    
                    interspersedD: echo(text:"after c:")
                }
            }
        '''

        when:
        def initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        initialResult.errors.isEmpty()
        initialResult.data == ["post": [
                "interspersedA": "before a:",
                "a"            : null,
                "interspersedB": "before b:",
                "b"            : null,
                "interspersedC": "before c:",
                "c"            : null,
                "interspersedD": "after c:",
        ]]

        when:

        Publisher<DeferredExecutionResult> deferredResultStream = initialResult.extensions[GraphQL.DEFERRED_RESULTS] as Publisher<DeferredExecutionResult>

        def subscriber = new CapturingSubscriber()
        subscriber.subscribeTo(deferredResultStream);
        Awaitility.await().untilTrue(subscriber.finished)

        List<DeferredExecutionResult> resultList = subscriber.executionResults

        then:

        assertDeferredData(resultList)
    }

    def assertDeferredData(ArrayList<DeferredExecutionResult> resultList) {
        resultList.size() == 6

        assert resultList[0].data == [[commentText: "comment0"], [commentText: "comment1"], [commentText: "comment2"]]
        assert resultList[0].errors == []
        assert resultList[0].path == ["post", "a"]

        assert resultList[1].data == [[reviewText: "review0", comments: null], [reviewText: "review1", comments: null], [reviewText: "review2", comments: null]]
        assert resultList[1].errors == []
        assert resultList[1].path == ["post", "b"]

        // exceptions in here
        assert resultList[2].errors.size() == 3
        assert resultList[2].errors[0].getMessage() == "Exception while fetching data (/post/c[0]/goes/bang) : Bang!"
        assert resultList[2].errors[1].getMessage() == "Exception while fetching data (/post/c[1]/goes/bang) : Bang!"
        assert resultList[2].errors[2].getMessage() == "Exception while fetching data (/post/c[2]/goes/bang) : Bang!"
        assert resultList[2].path == ["post", "c"]

        // sub defers are sent in encountered order
        assert resultList[3].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        assert resultList[3].errors == []
        assert resultList[3].path == ["post", "b", 0, "comments"]

        assert resultList[4].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        assert resultList[4].errors == []
        assert resultList[4].path == ["post", "b", 1, "comments"]

        assert resultList[5].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        assert resultList[5].errors == []
        assert resultList[5].path == ["post", "b", 2, "comments"]

        true
    }

    def "nonNull types are not allowed"() {

        def query = '''
            {
                mandatoryReviews @defer # nulls are not allowed
                {
                    reviewText
                }
            }
        '''
        when:
        def initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())
        then:
        initialResult.errors.size() == 1
        initialResult.errors[0].errorType == ErrorType.ValidationError
        (initialResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferDirectiveOnNonNullField

    }

    def "mutations cant have defers"() {

        def query = '''
            mutation {
                mutate(arg : "go") @defer
            }
        '''
        when:
        def initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())
        then:
        initialResult.errors.size() == 1
        initialResult.errors[0].errorType == ErrorType.ValidationError
        (initialResult.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferDirectiveNotOnQueryOperation
    }

    private ExecutionResult executeQuery(String query) {
        return this.executeQuery(query, true, [:])
    }

    private ExecutionResult executeQuery(String query, Map<String, Object> variables) {
        return this.executeQuery(query, true, variables)
    }

    private ExecutionResult executeQuery(String query, boolean incrementalSupport, Map<String, Object> variables) {
        return graphQL.execute(
                ExecutionInput.newExecutionInput()
                        .incrementalSupport(incrementalSupport)
                        .query(query)
                        .variables(variables)
                        .build()
        )
    }

    private static List<Map<String, Object>> getIncrementalResults(IncrementalExecutionResult initialResult) {
        Publisher<DelayedIncrementalExecutionResult> deferredResultStream = initialResult.incrementalItemPublisher

        def subscriber = new CapturingSubscriber()
        subscriber.subscribeTo(deferredResultStream)
        Awaitility.await().untilTrue(subscriber.finished)

        return subscriber.executionResults
                .collect { it.toSpecification() }
    }
}
