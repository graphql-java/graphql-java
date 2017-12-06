package graphql.execution

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.Message
import graphql.execution.pubsub.ReactiveStreamsMessagePublisher
import graphql.execution.pubsub.RxJavaMessagePublisher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

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


    @Unroll
    def "subscription query sends out a stream of events using the '#why' implementation"() {

        given:
        Publisher<Object> publisher = eventStreamPublisher

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                assert environment.getArgument("roomId") == 123
                return publisher
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

        def executionResult = graphQL.execute(executionInput)

        when:
        Publisher<ExecutionResult> msgStream = executionResult.getData()
        def capturingSubscriber = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        def messages = capturingSubscriber.events
        messages.size() == 10
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i].data
            assert message == ["newMessage": [sender: "sender" + i, text: "text" + i]]
        }

        where:
        why                       | eventStreamPublisher
        'reactive streams stream' | new ReactiveStreamsMessagePublisher(10)
        'rxjava stream'           | new RxJavaMessagePublisher(10)

    }

    @Unroll
    def "subscription alias is correctly used in response messages using '#why' implementation"() {

        given:
        Publisher<Object> publisher = eventStreamPublisher

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                assert environment.getArgument("roomId") == 123
                return publisher
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewMessages {
              newsFeed: newMessage(roomId: 123) {
                sender
                text
              }
            }
        """).build()

        def executionResult = graphQL.execute(executionInput)

        when:
        Publisher<ExecutionResult> msgStream = executionResult.getData()
        def capturingSubscriber = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        def messages = capturingSubscriber.events
        messages.size() == 1
        messages[0].data == ["newsFeed": [sender: "sender0", text: "text0"]]

        where:
        why                       | eventStreamPublisher
        'reactive streams stream' | new ReactiveStreamsMessagePublisher(1)
        'rxjava stream'           | new RxJavaMessagePublisher(1)
    }


    @Unroll
    def "multiple subscribers can get messages on a subscription query using '#why' implementation "() {

        //
        // In practice people aren't likely to use multiple subscription I think BUT since its reactive stream
        // capability and it costs us little to support it, lets have a test for it.
        //
        given:
        Publisher<Object> publisher = eventStreamPublisher

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                assert environment.getArgument("roomId") == 123
                return publisher
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

        def executionResult = graphQL.execute(executionInput)

        when:
        Publisher<ExecutionResult> msgStream = executionResult.getData()
        def capturingSubscriber1 = new CapturingSubscriber<ExecutionResult>()
        def capturingSubscriber2 = new CapturingSubscriber<ExecutionResult>()
        msgStream.subscribe(capturingSubscriber1)
        msgStream.subscribe(capturingSubscriber2)

        then:
        Awaitility.await().untilTrue(capturingSubscriber1.isDone())
        Awaitility.await().untilTrue(capturingSubscriber2.isDone())

        capturingSubscriber1.events.size() == 10
        capturingSubscriber2.events.size() == 10

        where:
        why                       | eventStreamPublisher
        'reactive streams stream' | new ReactiveStreamsMessagePublisher(10)
        'rxjava stream'           | new RxJavaMessagePublisher(10)

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
                return new ReactiveStreamsMessagePublisher(10) {
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
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        def messages = capturingSubscriber.events
        messages.size() == 5
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i].data
            assert message == ["newMessage": [sender: "sender" + i, text: "text" + i]]
        }

        capturingSubscriber.getThrowable().getMessage() == "Bang!"
    }


    def "subscription query will surface errors during completeField errors"() {

        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return new ReactiveStreamsMessagePublisher(10) {
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
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        def messages = capturingSubscriber.events
        messages.size() == 10
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i]
            if (i == 5) {
                message.data == null
                assert message.errors.size() == 2
                assert message.errors[0].errorType == ErrorType.DataFetchingException
                assert message.errors[0].message == "Cannot return null for non-nullable type: 'String' within parent 'Message' (/newMessage/sender)"

                assert message.errors[1].errorType == ErrorType.DataFetchingException
                assert message.errors[1].message == "Cannot return null for non-nullable type: 'String' within parent 'Message' (/newMessage/text)"
            } else {
                assert message.data == ["newMessage": [sender: "sender" + i, text: "text" + i]]
            }
        }
    }
}
