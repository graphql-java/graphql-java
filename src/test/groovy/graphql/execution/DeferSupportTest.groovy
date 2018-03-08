package graphql.execution

import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import javafx.util.Duration
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DeferSupportTest extends Specification {
    def then = System.currentTimeMillis();

    def sentAt() {
        def seconds = Duration.millis(System.currentTimeMillis() - then).toSeconds()
        "T+" + seconds
    }

    def sleepSome(DataFetchingEnvironment env) {
        Integer sleepTime = env.getArgument("sleepTime")
        sleepTime = Optional.ofNullable(sleepTime).orElse(0)
        Thread.sleep(sleepTime)
    }


    def "test defer support"() {
        def spec = '''
            type Query {
                post : Post 
            }
            
            type Post {
                postText : String
                sentAt : String
                comments(sleepTime : Int) : [Comment]
                reviews(sleepTime : Int) : [Review]
            }
            
            type Comment {
                commentText : String
                sentAt : String
                goes : Bang
            }
            
            type Review {
                reviewText : String
                sentAt : String
                goes : Bang
            }       
            
            type Bang {
                bang : String
            }     
        '''

        DataFetcher postFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return [postText: "This is the postText", sentAt: sentAt()]
            }
        }
        DataFetcher commentsFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment env) {
                return CompletableFuture.supplyAsync({
                    sleepSome(env)
                    def result = []
                    for (int i = 0; i < 5; i++) {
                        result.add([commentText: "This is the comment text " + i, sentAt: sentAt(), goes: "goes"])
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
                        result.add([reviewText: "This is the review text " + i, sentAt: sentAt(), goes: "goes"])
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

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetcher("post", postFetcher))
                .type(newTypeWiring("Post").dataFetcher("comments", commentsFetcher))
                .type(newTypeWiring("Post").dataFetcher("reviews", reviewsFetcher))
                .type(newTypeWiring("Bang").dataFetcher("bang", bangDataFetcher))
                .build()

        def schema = TestUtil.schema(spec, runtimeWiring)
        schema = schema.transform({ builder ->
            builder.additionalDirective(Directives.DeferDirective)
        })

        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = '''
            query {
                post {
                    postText
                    sentAt
                    comments(sleepTime:1000) @defer {
                        commentText
                        sentAt
                    }
                    reviews(sleepTime:2000) @defer {
                        reviewText
                        sentAt
                    }
                    bang: reviews @defer {
                        sentAt
                        goes {
                            bang
                        }
                    }
                }
            }
        '''

        when:
        def result = graphQL.execute(ExecutionInput.newExecutionInput().query(query).build())

        then:
        result.errors.isEmpty()
        result.data != null
        println result.data


        Publisher<ExecutionResult> deferredResults = result.extensions["deferredResults"] as Publisher<ExecutionResult>
        deferredResults.subscribe(new Subscriber<ExecutionResult>() {
            @Override
            void onSubscribe(Subscription s) {
                println "\nonSubscribe@" + sentAt()
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                println "\nonNext@" + sentAt()
                println executionResult.data
                println executionResult.errors
            }

            @Override
            void onError(Throwable t) {
                println "\nonError@" + sentAt()
                t.printStackTrace()
            }

            @Override
            void onComplete() {
                println "\nonComplete@" + sentAt()
            }
        })
    }
}
