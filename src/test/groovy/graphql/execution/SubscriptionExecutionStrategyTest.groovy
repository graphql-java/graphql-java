package graphql.execution

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.Message
import graphql.execution.pubsub.MessagePublisher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.ForkJoinPool

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class SubscriptionExecutionStrategyTest extends Specification {

    def idl = """
            type Query {
                name : String
                age : Int
            }
            
            type Message {
                sender : String!
                text : String!
            }
            
            type Subscription {
                newMessage(roomId:Int) : Message
            }
        """

    GraphQL buildSubscriptionQL(DataFetcher newMessageDF) {
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Subscription").dataFetcher("newMessage", newMessageDF).build())
                .build()

        def schema = TestUtil.schema(idl, runtimeWiring)

        def graphQL = GraphQL.newGraphQL(schema).subscriptionExecutionStrategy(new SubscriptionExecutionStrategy()).build()
        graphQL
    }


    def "subscription query sends out a stream of events"() {

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                assert environment.getArgument("roomId") == 123
                return new MessagePublisher(10, ForkJoinPool.commonPool())
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewMessages {
              newMessage(roomId: 123) {
                sender
                text
              }
            }
        """).build()

        when:
        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult != null

        when:
        Publisher<ExecutionResult> msgStream = executionResult.getData()
        def capturingSubscriber = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.getDone())

        def messages = capturingSubscriber.events
        messages.size() == 10
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i].data
            message == [sender: "sender" + i, text: "text" + i]
        }
    }

    def "subscription query will surface fetch errors"() {

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                throw new RuntimeException("Bang")
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewMessages {
              newMessage(roomId: 123) {
                sender
                text
              }
            }
        """).build()

        when:
        def executionResult = graphQL.execute(executionInput)

        then:
        executionResult != null
        executionResult.data == null
        executionResult.errors.size() == 1
    }

    def "subscription query will surface event stream exceptions"() {

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return new MessagePublisher(10, ForkJoinPool.commonPool()) {
                    //
                    // we blow up half way in
                    @Override
                    protected Message examineMessage(Message message, Integer at) {
                        if (at == 5) {
                            throw new RuntimeException("Bang!")
                        }
                        return message
                    }
                }
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewMessages {
              newMessage(roomId: 123) {
                sender
                text
              }
            }
        """).build()

        when:

        def executionResult = graphQL.execute(executionInput)

        Publisher<ExecutionResult> msgStream = executionResult.getData()

        def capturingSubscriber = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.getDone())

        def messages = capturingSubscriber.events
        messages.size() == 5
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i].data
            message == [sender: "sender" + i, text: "text" + i]
        }

        capturingSubscriber.getThrowable().getMessage() == "Bang!"
    }


    def "subscription query will surface errors during completeField errors"() {

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return new MessagePublisher(10, ForkJoinPool.commonPool()) {
                    //
                    // we blow up half way in
                    @Override
                    protected Message examineMessage(Message message, Integer at) {
                        if (at == 5) {
                            //
                            // the fields are non null - which will cause the 5th execution result to be in error
                            return new Message(null, null)
                        }
                        return message
                    }
                }
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewMessages {
              newMessage(roomId: 123) {
                sender
                text
              }
            }
        """).build()

        when:

        def executionResult = graphQL.execute(executionInput)

        Publisher<ExecutionResult> msgStream = executionResult.getData()

        def capturingSubscriber = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.getDone())

        def messages = capturingSubscriber.events
        messages.size() == 10
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i]
            if (i == 5) {
                message.data == null
                message.errors.size() == 2
                message.errors[0].errorType == ErrorType.DataFetchingException
                message.errors[0].message == "Cannot return null for non-nullable type: 'String' within parent 'Message' (/newMessage/sender)"

                message.errors[1].errorType == ErrorType.DataFetchingException
                message.errors[1].message == "Cannot return null for non-nullable type: 'String' within parent 'Message' (/newMessage/text)"
            } else {
                message.data == [sender: "sender" + i, text: "text" + i]
            }
        }


    }
}
