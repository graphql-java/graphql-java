package graphql.execution.defer

import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DeferSupportIntegrationTest extends Specification {
    def then = 0

    def sentAt() {
        def seconds = Duration.ofMillis(System.currentTimeMillis() - then).getSeconds()
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
            }
            
            type Post {
                postText : String
                sentAt : String
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

    GraphQL graphQL = null

    void setup() {
        then = System.currentTimeMillis()

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("post", postFetcher))
                .type(newTypeWiring("Post").dataFetcher("comments", commentsFetcher))
                .type(newTypeWiring("Post").dataFetcher("reviews", reviewsFetcher))
                .type(newTypeWiring("Bang").dataFetcher("bang", bangDataFetcher))

                .type(newTypeWiring("Comment").dataFetcher("comments", commentsFetcher))
                .type(newTypeWiring("Review").dataFetcher("comments", commentsFetcher))
                .build()
        def schema = TestUtil.schema(schemaSpec, runtimeWiring)
        schema = schema.transform({ builder ->
            builder.additionalDirective(Directives.DeferDirective)
        })

        graphQL = GraphQL.newGraphQL(schema).build()
    }

    def "test defer support end to end"() {

        def query = '''
            query {
                post {
                    postText
                    
                    a :comments @defer {
                        commentText
                    }
                    
                    b : reviews @defer {
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
        initialResult.data == ["post": ["postText": "post_data"]]

        when:
        List<ExecutionResult> resultList = []

        Publisher<ExecutionResult> deferredResultStream = initialResult.extensions["deferredResultStream"] as Publisher<ExecutionResult>
        AtomicBoolean doneORCancelled = new AtomicBoolean()
        deferredResultStream.subscribe(new Subscriber<ExecutionResult>() {
            @Override
            void onSubscribe(Subscription s) {
                println "\nonSubscribe@" + sentAt()
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                println "\nonNext@" + sentAt()
                println executionResult.data
                println executionResult.errors
                resultList.add(executionResult)

            }

            @Override
            void onError(Throwable t) {
                doneORCancelled.set(true)
                println "\nonError@" + sentAt()
                t.printStackTrace()
            }

            @Override
            void onComplete() {
                doneORCancelled.set(true)
                println "\nonComplete@" + sentAt()
            }
        })

        then:
        Awaitility.await().untilTrue(doneORCancelled)

        resultList.size() == 6

        resultList[0].data == [[commentText: "comment0"], [commentText: "comment1"], [commentText: "comment2"]]
        resultList[0].errors == []

        resultList[1].data == [[reviewText: "review0"], [reviewText: "review1"], [reviewText: "review2"]]
        resultList[1].errors == []

        // exceptions in here
        resultList[2].errors.size() == 3
        resultList[2].errors[0].getMessage() == "Exception while fetching data ([0]/goes/bang) : Bang!"
        resultList[2].errors[1].getMessage() == "Exception while fetching data ([1]/goes/bang) : Bang!"
        resultList[2].errors[2].getMessage() == "Exception while fetching data ([2]/goes/bang) : Bang!"

        // sub defers are sent in encountered order
        resultList[3].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        resultList[3].errors == []

        resultList[4].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        resultList[4].errors == []

        resultList[5].data == [[commentText: "b_comment0"], [commentText: "b_comment1"], [commentText: "b_comment2"]]
        resultList[5].errors == []

    }
}
