package graphql.execution.defer

import graphql.DeferredExecutionResult
import graphql.ErrorType
import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DeferSupportIntegrationTest extends Specification {
    def then = 0

    def sentAt() {
        def seconds = Duration.ofMillis(System.currentTimeMillis() - then).toMillis()
        "T+" + seconds
    }

    def sleepSome(DataFetchingEnvironment env) {
        Integer sleepTime = env.getArgument("sleepTime")
        sleepTime = Optional.ofNullable(sleepTime).orElse(0)
        Thread.sleep(sleepTime)
    }

    def schemaSpec = '''
            type Query {
                post : Post 
                mandatoryReviews : [Review]!
            }
            
            type Mutation {
                mutate(arg : String) : String
            }
            
            type Post {
                postText : String
                sentAt : String
                echo(text : String = "echo") : String
                comments(sleepTime : Int, prefix :String) : [Comment]
                reviews(sleepTime : Int) : [Review]
            }
            
            type Comment {
                commentText : String
                sentAt : String
                comments(sleepTime : Int, prefix :String) : [Comment]
                goes : Bang
            }
            
            type Review {
                reviewText : String
                sentAt : String
                comments(sleepTime : Int, prefix :String) : [Comment]
                goes : Bang
            }       
            
            type Bang {
                bang : String
            }     
        '''

    DataFetcher postFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            return CompletableFuture.supplyAsync({
                [postText: "post_data", sentAt: sentAt()]
            })
        }
    }
    DataFetcher commentsFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment env) {
            return CompletableFuture.supplyAsync({
                sleepSome(env)

                def prefix = env.getArgument("prefix")
                prefix = prefix == null ? "" : prefix

                def result = []
                for (int i = 0; i < 3; i++) {
                    result.add([commentText: prefix + "comment" + i, sentAt: sentAt(), goes: "goes"])
                }
                return result
            })
        }

    }
    DataFetcher reviewsFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment env) {
            return CompletableFuture.supplyAsync({
                sleepSome(env)
                def result = []
                for (int i = 0; i < 3; i++) {
                    result.add([reviewText: "review" + i, sentAt: sentAt(), goes: "goes"])
                }
                return result
            })
        }
    }

    DataFetcher bangDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            throw new RuntimeException("Bang!")
        }
    }
    DataFetcher echoDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            return environment.getArgument("text")
        }
    }

    GraphQL graphQL = null

    void setup() {
        then = System.currentTimeMillis()

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("post", postFetcher))
                .type(newTypeWiring("Post").dataFetcher("comments", commentsFetcher))
                .type(newTypeWiring("Post").dataFetcher("echo", echoDataFetcher))
                .type(newTypeWiring("Post").dataFetcher("reviews", reviewsFetcher))
                .type(newTypeWiring("Bang").dataFetcher("bang", bangDataFetcher))

                .type(newTypeWiring("Comment").dataFetcher("comments", commentsFetcher))
                .type(newTypeWiring("Review").dataFetcher("comments", commentsFetcher))
                .build()

        def schema = TestUtil.schema(schemaSpec, runtimeWiring)
                .transform({ builder -> builder.additionalDirective(Directives.DeferDirective) })
        this.graphQL = GraphQL.newGraphQL(schema).build()
    }

    def "test defer support end to end"() {

        def query = '''
            query {
                post {
                    postText
                    
                    a :comments(sleepTime:200) @defer {
                        commentText
                    }
                    
                    b : reviews(sleepTime:100) @defer {
                        reviewText
                        comments(prefix : "b_") @defer {
                            commentText
                        }
                    }

                    c: reviews @defer {
                        goes {
                            bang
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
}
