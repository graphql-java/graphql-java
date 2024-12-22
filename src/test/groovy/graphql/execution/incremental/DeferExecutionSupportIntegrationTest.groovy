package graphql.execution.incremental

import com.google.common.collect.Iterables
import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.TestUtil
import graphql.execution.DataFetcherResult
import graphql.execution.pubsub.CapturingSubscriber
import graphql.incremental.DelayedIncrementalPartialResult
import graphql.incremental.IncrementalExecutionResult
import graphql.incremental.IncrementalExecutionResultImpl
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DeferExecutionSupportIntegrationTest extends Specification {
    def schemaSpec = '''
            type Query {
                post : Post 
                posts: [Post]
                postById(id: ID!): Post
                hello: String
                item(type: String!): Item 
            }
            
            interface Item {
                id: ID!
                summary: String
                text: String
            }
            
            type Page implements Item {
                id: ID!
                summary: String
                text: String
                views: Int
            }
            
            type Post implements Item {
                id: ID!
                summary : String
                text : String
                latestComment: Comment
                comments: [Comment]
                resolvesToNull: String
                dataFetcherError: String
                dataAndError: String
                coercionError: Int 
                typeMismatchError: [String]
                nonNullableError: String!
                wordCount: Int
            }
            
            type Comment {
                title: String
                content: String
                author: Person 
            }
            
            type Person {
                name: String
                avatar: String
            }
            
            type Mutation {
               addPost: Post 
            }    
        '''

    GraphQL graphQL = null

    private static DataFetcher resolve(Object value) {
        return resolve(value, 0, false)
    }

    private static DataFetcher resolve(Object value, Integer sleepMs) {
        return resolve(value, sleepMs, false)
    }

    private static DataFetcher resolve(Object value, Integer sleepMs, boolean allowMultipleCalls) {
        return new DataFetcher() {
            boolean executed = false

            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                if (executed && !allowMultipleCalls) {
                    throw new IllegalStateException("This data fetcher can run only once")
                }
                executed = true
                return CompletableFuture.supplyAsync {
                    Thread.sleep(sleepMs)
                    return value
                }
            }
        }
    }

    private static DataFetcher resolveItem() {
        return (env) -> {
            def type = env.getArgument("type")

            return CompletableFuture.supplyAsync { [__typename: type, id: "1001"] }
        }
    }

    private static TypeResolver itemTypeResolver() {
        return (env) -> {
            env.getSchema().getObjectType(env.object["__typename"])
        }
    }

    private static DataFetcher resolveWithException() {
        return new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                throw new RuntimeException("Bang!!!")
            }
        }
    }

    private static DataFetcher resolveWithDataAndError(Object data) {
        return new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                return DataFetcherResult.newResult()
                        .data(data)
                        .error(
                                GraphqlErrorBuilder.newError()
                                        .message("Bang!")
                                        .build()
                        )
                        .build()
            }
        }
    }

    void setup() {
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("post", resolve([id: "1001"]))
                        .dataFetcher("posts", resolve([
                                [id: "1001"],
                                [id: "1002"],
                                [id: "1003"]
                        ]))
                        .dataFetcher("postById", (env) -> {
                            return [id: env.getArgument("id")]
                        })
                        .dataFetcher("hello", resolve("world"))
                        .dataFetcher("item", resolveItem())
                )
                .type(newTypeWiring("Post").dataFetcher("summary", resolve("A summary", 10)))
                .type(newTypeWiring("Post").dataFetcher("text", resolve("The full text", 100)))
                .type(newTypeWiring("Post").dataFetcher("wordCount", resolve(45999, 10, true)))
                .type(newTypeWiring("Post").dataFetcher("latestComment", resolve([title: "Comment title"], 10)))
                .type(newTypeWiring("Post").dataFetcher("dataFetcherError", resolveWithException()))
                .type(newTypeWiring("Post").dataFetcher("dataAndError", resolveWithDataAndError("data")))
                .type(newTypeWiring("Post").dataFetcher("coercionError", resolve("Not a number", 10)))
                .type(newTypeWiring("Post").dataFetcher("typeMismatchError", resolve([a: "A Map instead of a List"], 10)))
                .type(newTypeWiring("Post").dataFetcher("nonNullableError", resolve(null)))
                .type(newTypeWiring("Page").dataFetcher("summary", resolve("A page summary", 10)))
                .type(newTypeWiring("Page").dataFetcher("text", resolve("The page full text", 100)))
                .type(newTypeWiring("Comment").dataFetcher("content", resolve("Full content", 100)))
                .type(newTypeWiring("Comment").dataFetcher("author", resolve([name: "Author name"], 10)))
                .type(newTypeWiring("Person").dataFetcher("avatar", resolve("Avatar image", 100)))
                .type(newTypeWiring("Mutation")
                        .dataFetcher("addPost", resolve([id: "1001"]))
                )
                .type(newTypeWiring("Item")
                        .typeResolver(itemTypeResolver()))
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

    def "defer with aliased fields"() {
        def query = '''
            query {
                postAlias: post {
                    idAlias: id
                    ... @defer {
                        summaryAlias: summary
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [postAlias: [idAlias: "1001"]],
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
                                        path: ["postAlias"],
                                        data: [summaryAlias: "A summary"]
                                ]
                        ]
                ]
        ]
    }

    def "aliased fields with different parameters"() {
        def query = '''
            query {
                postById(id: "1") {
                    id
                }
                ... @defer {
                    post2: postById(id: "2") {
                        id2: id
                    }
                }
                ... @defer(label: "defer-post3") {
                    post3: postById(id: "3") {
                        ... @defer(label: "defer-id3") {
                            id3: id
                        }
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [postById: [id: "1"]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        // Ordering is non-deterministic, so we assert on the things we know are going to be true.

        incrementalResults.size() == 3
        // only the last payload has "hasNext=true"
        incrementalResults[0].hasNext == true
        incrementalResults[1].hasNext == true
        incrementalResults[2].hasNext == false

        // every payload has only 1 incremental item
        incrementalResults.every { it.incremental.size() == 1 }

        incrementalResults.any {
            it.incremental[0] == [path: [], data: [post2: [id2: "2"]]]
        }

        // id3 HAS TO be delivered after post3
        def indexOfPost3 = Iterables.indexOf(incrementalResults, {
            it.incremental[0] == [path: [], label: "defer-post3", data: [post3: [:]]]
        })

        def indexOfId3 = Iterables.indexOf(incrementalResults, {
            it.incremental[0] == [path: ["post3"], label: "defer-id3", data: [id3: "3"]]
        })

        // Assert that both post3 and id3 are present
        indexOfPost3 >= 0
        indexOfId3 >= 0
        // Assert that id3 is delivered after post3
        indexOfId3 > indexOfPost3
    }

    def "defer on interface field"() {
        def query = """
            query {
                item(type: "$type") {
                    __typename
                    id
                    ... on Item @defer {
                        summary
                    }
                   
                    ... on Post {
                        text
                    } 
                    
                    ... on Page @defer {
                        text
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

        then:
        if (type == "Post") {
            assert initialResult.toSpecification() == [
                    data   : [item: [__typename: "Post", id: "1001", text: "The full text"]],
                    hasNext: true
            ]
        } else {
            assert initialResult.toSpecification() == [
                    data   : [item: [__typename: "Page", id: "1001"]],
                    hasNext: true
            ]
        }

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        if (type == "Post") {
            assert incrementalResults == [
                    [
                            hasNext    : false,
                            incremental: [
                                    [
                                            path: ["item"],
                                            data: [summary: "A summary"]
                                    ]
                            ]
                    ]
            ]
        } else {
            assert incrementalResults == [
                    [
                            hasNext    : true,
                            incremental: [
                                    [
                                            path: ["item"],
                                            data: [summary: "A page summary"]
                                    ]
                            ]
                    ],
                    [
                            hasNext    : false,
                            incremental: [
                                    [
                                            path: ["item"],
                                            data: [text: "The page full text"]
                                    ]
                            ]
                    ]
            ]
        }

        where:
        type << ["Page", "Post"]
    }

    def "defer execution is ignored if support for incremental delivery is disabled"() {
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
        ExecutionResult initialResult = executeQuery(query, false, [:])

        then:
        !(initialResult instanceof IncrementalExecutionResult)

        initialResult.toSpecification() == [
                data: [post: [id: "1001", summary: "A summary"]],
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

    def "defer with null label should behave as if no label was provided"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer(label: null) {
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

    def "deferred field results in 'null'"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer {
                        resolvesToNull
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
                                        data: [resolvesToNull: null]
                                ]
                        ]
                ]
        ]
    }

    def "simple defer with fragment definition"() {
        def query = '''
            query {
                post {
                    id
                    ... PostData @defer
                }
            }
            
            fragment PostData on Post {
                summary
                text
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
                                        path : ["post"],
                                        data : [summary: "A summary"]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        label: "text-defer",
                                        path : ["post"],
                                        data : [text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "order of @defer definition in query doesn't affect order of incremental payloads in response"() {
        def query = '''
            query {
                post {
                    id
                    # "text" is defined before "summary" in the query, but it's slower, so it will be delivered after.
                    ... @defer {
                        text
                    }
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
                        hasNext    : true,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [summary: "A summary"]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "keeps the fields named correctly when interspersed in the query"() {
        def query = '''
            query {
                post {
                    firstId: id
                    ... @defer {
                        text
                    }
                    secondId: id
                    ... @defer {
                        summary
                    }
                    thirdId: id
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [firstId: "1001", secondId: "1001", thirdId: "1001"]],
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
                                        path: ["post"],
                                        data: [summary: "A summary"]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "defer result in initial result being empty object"() {
        def query = '''
            query {
                post {
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
                data   : [post: [:]],
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

    def "defer on top level field"() {
        def query = '''
            query {
                hello
                ... @defer {
                    post {
                        id
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [hello: "world"],
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
                                        path: [],
                                        data: [post: [id: "1001"]]
                                ]
                        ]
                ]
        ]
    }

    def "nested defers"() {
        def query = '''
            query {
                ... @defer {
                    post {
                        id
                        ... @defer {
                            summary
                            latestComment {
                                title
                                ... @defer {
                                    content
                                }
                            }
                        }
                    }
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [:],
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
                                        path: [],
                                        data: [post: [id: "1001"]]
                                ]
                        ]
                ],
                [
                        hasNext    : true,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [summary: "A summary", latestComment: [title: "Comment title"]]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post", "latestComment"],
                                        data: [content: "Full content"]
                                ]
                        ]
                ]
        ]
    }

    def "multiple defers on same field"() {

        def query = '''
            query {
                post {
                    ... @defer {
                        summary
                    }
                    ... @defer(label: "defer-outer") {
                        summary
                        ... @defer(label: "defer-inner") {
                            summary
                        }
                    }
                }
            }
        '''

        when:

        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: [:]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        // Ordering is non-deterministic, so we assert on the things we know are going to be true.

        incrementalResults.size() == 3
        // only the last payload has "hasNext=true"
        incrementalResults[0].hasNext == true
        incrementalResults[1].hasNext == true
        incrementalResults[2].hasNext == false

        // every payload has only 1 incremental item, and the data is the same for all of them
        incrementalResults.every { it.incremental.size() == 1 }
        incrementalResults.every { it.incremental[0].data == [summary: "A summary"] }

        // "label" is different for every payload
        incrementalResults.any { it.incremental[0].label == null }
        incrementalResults.any { it.incremental[0].label == "defer-inner" }
        incrementalResults.any { it.incremental[0].label == "defer-outer" }
    }

    def "mutations can have defers"() {
        def query = '''
            mutation {
                addPost {
                    firstId: id
                    ... @defer {
                        text
                    }
                    secondId: id
                    ... @defer {
                        summary
                    }
                    thirdId: id
                }
            }
        '''

        when:
        IncrementalExecutionResult initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [addPost: [firstId: "1001", secondId: "1001", thirdId: "1001"]],
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
                                        path: ["addPost"],
                                        data: [summary: "A summary"]
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["addPost"],
                                        data: [text: "The full text"]
                                ]
                        ]
                ]
        ]
    }

    def "can handle error raised by data fetcher"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer {
                        dataFetcherError
                    }
                    ... @defer {
                        text
                    }
                }
            }
        '''

        when:
        def initialResult = executeQuery(query)

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
                                        path  : ["post"],
                                        data  : [dataFetcherError: null],
                                        errors: [[
                                                         message   : "Exception while fetching data (/post/dataFetcherError) : Bang!!!",
                                                         locations : [[line: 6, column: 25]],
                                                         path      : ["post", "dataFetcherError"],
                                                         extensions: [classification: "DataFetchingException"]
                                                 ]],
                                ],
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle data fetcher that returns both data and error on nested field"() {
        def query = '''
            query {
                hello
                ... @defer {
                    post {
                        dataAndError
                    }
                }               
            }
        '''

        when:
        def initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [hello: "world"],
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
                                        path  : [],
                                        data  : [post: [dataAndError: "data"]],
                                        errors: [[
                                                         message   : "Bang!",
                                                         locations : [],
                                                         extensions: [classification: "DataFetchingException"]
                                                 ]],
                                ],
                        ]
                ],
        ]
    }

    def "can handle data fetcher that returns both data and error"() {
        def query = '''
            query {
                post {
                    id
                    ... @defer {
                        dataAndError
                    }
                    ... @defer {
                        text
                    }
                }
            }
        '''

        when:
        def initialResult = executeQuery(query)

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
                                        path  : ["post"],
                                        data  : [dataAndError: "data"],
                                        errors: [[
                                                         message   : "Bang!",
                                                         locations : [],
                                                         extensions: [classification: "DataFetchingException"]
                                                 ]],
                                ],
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle UnresolvedTypeException"() {
        def query = """
            query {
                post {
                    id
                    ... @defer {
                        text 
                    }
                }
                ... @defer {
                    item(type: "NonExistingType") {
                        id
                        summary
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

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
                                        path  : [],
                                        data  : [item: null],
                                        errors: [
                                                [
                                                        message   : "Can't resolve '/item'. Abstract type 'Item' must resolve to an Object type at runtime for field 'Query.item'. Could not determine the exact type of 'Item'",
                                                        path      : ["item"],
                                                        extensions: [
                                                                classification: "DataFetchingException"
                                                        ]
                                                ]
                                        ],
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle coercion problem"() {
        def query = """
            query {
                post {
                    id
                    ... @defer {
                        text 
                    }
                    ... @defer {
                        coercionError
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

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
                                        path  : ["post"],
                                        data  : [coercionError: null],
                                        errors: [
                                                [
                                                        message   : "Can't serialize value (/post/coercionError) : Expected a value that can be converted to type 'Int' but it was a 'String'",
                                                        path      : ["post", "coercionError"],
                                                        extensions: [
                                                                classification: "DataFetchingException"
                                                        ]
                                                ]
                                        ],
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle type mismatch problem"() {
        def query = """
            query {
                post {
                    id
                    ... @defer {
                        text 
                    }
                    ... @defer {
                        typeMismatchError
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

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
                                        path  : ["post"],
                                        data  : [typeMismatchError: null],
                                        errors: [
                                                [
                                                        message   : "Can't resolve value (/post/typeMismatchError) : type mismatch error, expected type LIST",
                                                        path      : ["post", "typeMismatchError"],
                                                        extensions: [
                                                                classification: "DataFetchingException"
                                                        ]
                                                ]
                                        ],
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle non nullable error in one of the defer calls"() {
        def query = """
            query {
                post {
                    id
                    ... @defer {
                        text 
                    }
                    ... @defer {
                        summary
                        nonNullableError
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

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
                                        data  : null,
                                        path  : ["post"],
                                        errors: [
                                                [
                                                        message   : "The field at path '/post/nonNullableError' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is 'String' within parent type 'Post'",
                                                        path      : ["post", "nonNullableError"],
                                                        extensions: [
                                                                classification: "NullValueInNonNullableField"
                                                        ]
                                                ]
                                        ],
                                ]
                        ]
                ],
                [
                        hasNext    : false,
                        incremental: [
                                [
                                        path: ["post"],
                                        data: [text: "The full text"],
                                ]
                        ]
                ]
        ]
    }

    def "can handle non nullable error in the initial result"() {
        def query = """
            query {
                post {
                    id
                    nonNullableError
                    ... @defer {
                        summary
                    }
                }
            }
        """

        when:
        def initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [post: null],
                errors : [
                        [message   : "The field at path '/post/nonNullableError' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is 'String' within parent type 'Post'",
                         path      : ["post", "nonNullableError"],
                         extensions: [classification: "NullValueInNonNullableField"]
                        ]
                ],
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
                                        data: [summary: "A summary"],
                                ]
                        ]
                ]
        ]
    }

    def "defer on list items"() {
        def query = '''
            query {
                posts {
                    id
                    ... @defer {
                        wordCount
                    }
                }
            }
        '''

        when:
        def initialResult = executeQuery(query)

        then:
        initialResult.toSpecification() == [
                data   : [posts: [[id: "1001"], [id: "1002"], [id: "1003"]]],
                hasNext: true
        ]

        when:
        def incrementalResults = getIncrementalResults(initialResult)

        then:
        // Ordering is non-deterministic, so we assert on the things we know are going to be true.

        incrementalResults.size() == 3
        // only the last payload has "hasNext=true"
        incrementalResults[0].hasNext == true
        incrementalResults[1].hasNext == true
        incrementalResults[2].hasNext == false

        // every payload has only 1 incremental item, and the data is the same for all of them
        incrementalResults.every { it.incremental.size() == 1 }
        incrementalResults.every { it.incremental[0].data == [wordCount: 45999] }

        // path is different for every payload
        incrementalResults.any { it.incremental[0].path == ["posts", 0] }
        incrementalResults.any { it.incremental[0].path == ["posts", 1] }
        incrementalResults.any { it.incremental[0].path == ["posts", 2] }
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
                        .graphQLContext([(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                        .query(query)
                        .variables(variables)
                        .build()
        )
    }

    private static List<Map<String, Object>> getIncrementalResults(IncrementalExecutionResult initialResult) {
        Publisher<DelayedIncrementalPartialResult> deferredResultStream = initialResult.incrementalItemPublisher

        def subscriber = new CapturingSubscriber<DelayedIncrementalPartialResult>()

        deferredResultStream.subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.isDone())
        if (subscriber.throwable != null) {
            throw new RuntimeException(subscriber.throwable)
        }
        return subscriber.getEvents()
                .collect { it.toSpecification() }
    }
}
