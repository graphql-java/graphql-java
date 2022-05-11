package graphql.execution

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.TestUtil
import graphql.TypeMismatchError
import graphql.execution.instrumentation.TestingInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.Message
import graphql.execution.pubsub.ReactiveStreamsMessagePublisher
import graphql.execution.pubsub.ReactiveStreamsObjectPublisher
import graphql.execution.pubsub.RxJavaMessagePublisher
import graphql.execution.reactive.SubscriptionPublisher
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.PropertyDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

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
                newListOfMessages(roomId:Int): [Message]
            }
        """

    GraphQL buildSubscriptionQL(DataFetcher newMessageDF) {
        buildSubscriptionQL(newMessageDF, PropertyDataFetcher.fetching("sender"), PropertyDataFetcher.fetching("text"))
    }

    RuntimeWiring.Builder buildBaseSubscriptionWiring(DataFetcher senderDF, DataFetcher textDF) {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Message")
                        .dataFetcher("sender", senderDF)
                        .dataFetcher("text", textDF)
                        .build())
    }

    GraphQL buildSubscriptionListQL(DataFetcher newListOfMessagesDF) {
        return buildSubscriptionListQL(newListOfMessagesDF, PropertyDataFetcher.fetching("sender"), PropertyDataFetcher.fetching("text"))
    }

    GraphQL buildSubscriptionListQL(DataFetcher newListOfMessagesDF, DataFetcher senderDF, DataFetcher textDF) {
        RuntimeWiring runtimeWiring = buildBaseSubscriptionWiring(senderDF, textDF)
                .type(newTypeWiring("Subscription").dataFetcher("newListOfMessages", newListOfMessagesDF).build())
                .build()

        return TestUtil.graphQL(idl, runtimeWiring).subscriptionExecutionStrategy(new SubscriptionExecutionStrategy()).build()
    }

    GraphQL buildSubscriptionQL(DataFetcher newMessageDF, DataFetcher senderDF, DataFetcher textDF) {
        RuntimeWiring runtimeWiring = buildBaseSubscriptionWiring(senderDF, textDF)
                .type(newTypeWiring("Subscription").dataFetcher("newMessage", newMessageDF).build())
                .build()

        return TestUtil.graphQL(idl, runtimeWiring).subscriptionExecutionStrategy(new SubscriptionExecutionStrategy()).build()
    }

    static GraphQLError mkError(String message) {
        GraphqlErrorBuilder.newError().message(message).build()
    }

    @Unroll
    def "#2609 when a GraphQLList is expected and a non iterable or non array is passed then it should yield a TypeMismatch error (spec October2021/6.2.3.2.ExecuteSubscriptionEvent(...).5)"() {
        given:
        DataFetcher newListOfMessagesDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return new ReactiveStreamsMessagePublisher(5) {
                    @Override
                    protected Message examineMessage(Message message, Integer at) {
                        // Should actually be list of messages ([Message])
                        return new Message("aSender_" + at, "someText" + at)
                    }
                }
            }
        }

        GraphQL graphQL = buildSubscriptionListQL(newListOfMessagesDF)

        def executionInput = ExecutionInput.newExecutionInput().query("""
            subscription NewListOfMessages {
              newListOfMessages(roomId: 123) {
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
            def message = messages[i]
            assert message.errors.size() == 1
            assert message.errors[0] instanceof TypeMismatchError
            assert message.errors[0].message.contains("/newListOfMessages")
            assert message.errors[0].message.contains("LIST")
        }
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
        msgStream instanceof SubscriptionPublisher
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
                assert message.errors[0].errorType == ErrorType.NullValueInNonNullableField
                assert message.errors[0].message.contains("/newMessage/sender")

                assert message.errors[1].errorType == ErrorType.NullValueInNonNullableField
                assert message.errors[1].message.contains("/newMessage/text")
            } else {
                assert message.data == ["newMessage": [sender: "sender" + i, text: "text" + i]]
            }
        }
    }

    def "subscriptions can return DataFetcher results with errors"() {

        //
        // this tests that we can wrap the Publisher in a DataFetcherResult AND that the return types of the Publisher
        // can themselves be DataFetcherResult objects - hence DataFetcherResult<Publisher<DataFetcherResult<Message>>>
        // in this case
        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                def objectMaker = { int index ->
                    def message = new Message("sender" + index, "text" + index)
                    GraphQLError error = null
                    if (index == 1) {
                        error = mkError("1 is the loneliest number that you'll ever know")
                    }
                    // wrap inner result in DataFetcherResult
                    def resultBuilder = DataFetcherResult.newResult().data(message).localContext(index)
                    if (error != null) {
                        resultBuilder.error(error)
                    }
                    return resultBuilder.build()
                }
                def publisher = new ReactiveStreamsObjectPublisher(10, objectMaker)
                // we also use DFR here to wrap the publisher to show it can work
                return DataFetcherResult.newResult().data(publisher).error(mkError("The top level field publisher can have errors")).build()
            }

        }

        DataFetcher senderDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                Message msg = environment.getSource()
                if (msg.sender == "sender1") {
                    return DataFetcherResult.newResult().data(msg.sender).error(mkError("Sub level fields can have errors")).build()
                }
                return msg.sender
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF, senderDF, PropertyDataFetcher.fetching("text"))

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

        executionResult.errors.size() == 1
        executionResult.errors[0].message == "The top level field publisher can have errors"

        def messages = capturingSubscriber.events
        messages.size() == 10
        for (int i = 0; i < messages.size(); i++) {
            def message = messages[i]
            // error handling on publisher events
            if (i == 1) {
                assert message.errors[0].message == "1 is the loneliest number that you'll ever know"
                assert message.errors[1].message == "Sub level fields can have errors"
            } else {
                assert message.errors.isEmpty(), "There should be no errors present"
            }
            assert message.data == ["newMessage": [sender: "sender" + i, text: "text" + i]]
        }
    }

    def "subscriptions local context works as expected"() {

        //
        // make sure local context is preserved down the subscription
        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                def objectMaker = { int index ->
                    def message = new Message("sender" + index, "text" + index)
                    return DataFetcherResult.newResult().data(message).localContext(index).build()
                }
                def publisher = new ReactiveStreamsObjectPublisher(10, objectMaker)
                // we also use DFR here to wrap the publisher to show it can work
                return DataFetcherResult.newResult().data(publisher).build()
            }

        }

        DataFetcher senderDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) throws Exception {
                Message msg = environment.getSource()
                return msg.sender + "-lc:" + environment.getLocalContext()
            }
        }

        GraphQL graphQL = buildSubscriptionQL(newMessageDF, senderDF, PropertyDataFetcher.fetching("text"))

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
            def senderVal = "sender" + i + "-lc:" + i
            assert message.data == ["newMessage": [sender: senderVal, text: "text" + i]]
        }
    }

    def "instrumentation gets called on subscriptions"() {

        //
        // make sure local context is preserved down the subscription
        DataFetcher newMessageDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                new ReactiveStreamsObjectPublisher(10, { int index ->
                    new Message("sender" + index, "text" + index)
                })
            }
        }

        def instrumentResultCalls = []
        TestingInstrumentation instrumentation = new TestingInstrumentation() {
            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
                instrumentResultCalls.add("instrumentExecutionResult")
                return CompletableFuture.completedFuture(executionResult)
            }
        }
        GraphQL graphQL = buildSubscriptionQL(newMessageDF)
        graphQL = graphQL.transform({ builder -> builder.instrumentation(instrumentation) })

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

        def subscribedFieldCalls = instrumentation.executionList.findAll { s -> s.contains(":subscribed-field-event") }
        subscribedFieldCalls.size() == 20 // start and end calls
        subscribedFieldCalls.findAll { s -> s.contains("newMessage") }.size() == 20 // all for our subscribed field

        // this works because our calls are serial - if we truly had async values then the order would not work
        subscribedFieldCalls.withIndex().collect({ s, index ->
            if (index % 2 == 0) {
                assert s.startsWith("start:"), "expected start: on even indexes"
            } else {
                assert s.startsWith("end:"), "expected end: on odd indexes"
            }
        })

        instrumentResultCalls.size() == 11 // one for the initial execution and then one for each stream event
    }

}
