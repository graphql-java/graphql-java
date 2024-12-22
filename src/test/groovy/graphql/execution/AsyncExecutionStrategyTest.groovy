package graphql.execution

import graphql.ErrorType
import graphql.ExecutionResult
import graphql.ExperimentalApi
import graphql.GraphQLContext
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mergedField
import static graphql.TestUtil.mergedSelectionSet
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static org.awaitility.Awaitility.await

abstract class AsyncExecutionStrategyTest extends Specification {
    static boolean incrementalSupport

    def graphqlContextMock = Mock(GraphQLContext)

    GraphQLSchema schema(DataFetcher dataFetcher1, DataFetcher dataFetcher2) {
        def queryName = "RootQueryType"
        def field1Name = "hello"
        def field2Name = "hello2"

        GraphQLFieldDefinition.Builder fieldDefinition1 = newFieldDefinition()
                .name(field1Name)
                .type(GraphQLString)
        GraphQLFieldDefinition.Builder fieldDefinition2 = newFieldDefinition()
                .name(field2Name)
                .type(GraphQLString)

        def field1Coordinates = FieldCoordinates.coordinates(queryName, field1Name)
        def field2Coordinates = FieldCoordinates.coordinates(queryName, field2Name)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(field1Coordinates, dataFetcher1)
                .dataFetcher(field2Coordinates, dataFetcher2)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryName)
                        .field(fieldDefinition1)
                        .field(fieldDefinition2)
                        .build()
                )
                .build()

        schema
    }

    def setup() {
        graphqlContextMock.get(ENABLE_INCREMENTAL_SUPPORT) >> incrementalSupport
    }

    def "execution is serial if the dataFetchers are blocking"() {
        given:
        def lock = new ReentrantLock()
        def counter = new AtomicInteger()
        GraphQLSchema schema = schema(
                { env ->
                    assert lock.tryLock()
                    Thread.sleep(100)
                    def result = "world" + (counter.incrementAndGet())
                    lock.unlock()
                    result
                },
                { env ->
                    assert lock.tryLock()
                    def result = "world" + (counter.incrementAndGet())
                    lock.unlock()
                    result
                }
        )
        String query = "{hello, hello2}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .graphQLContext(graphqlContextMock)
                .locale(Locale.getDefault())
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField([Field.newField('hello').build()]), 'hello2': mergedField([Field.newField('hello2').build()])]))
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)


        then:
        result.isDone()
        result.get().data == ['hello': 'world1', 'hello2': 'world2']

    }

    def "execution with already completed futures"() {
        given:

        GraphQLSchema schema = schema(
                { env -> CompletableFuture.completedFuture("world") },
                { env -> CompletableFuture.completedFuture("world2") }
        )
        String query = "{hello, hello2}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .locale(Locale.getDefault())
                .graphQLContext(graphqlContextMock)
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField([Field.newField('hello').build()]), 'hello2': mergedField([Field.newField('hello2').build()])]))
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)


        then:
        result.isDone()
        result.get().data == ['hello': 'world', 'hello2': 'world2']
    }

    def "async execution"() {
        GraphQLSchema schema = schema(
                { env -> CompletableFuture.completedFuture("world") },
                { env ->
                    CompletableFuture.supplyAsync({ ->
                        Thread.sleep(100)
                        "world2"
                    })
                }
        )
        String query = "{hello, hello2}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .graphQLContext(graphqlContextMock)
                .locale(Locale.getDefault())
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField([Field.newField('hello').build()]), 'hello2': mergedField([Field.newField('hello2').build()])]))
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)


        then:
        !result.isDone()
        await().until({ result.isDone() })
        result.get().data == ['hello': 'world', 'hello2': 'world2']

    }

    def "exception while fetching data"() {
        GraphQLSchema schema = schema(
                { env -> CompletableFuture.completedFuture("world") },
                { env ->
                    throw new NullPointerException()
                }
        )
        String query = "{hello, hello2}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .locale(Locale.getDefault())
                .graphQLContext(graphqlContextMock)
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField([Field.newField('hello').build()]), 'hello2': mergedField([Field.newField('hello2').build()])]))
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)


        then:
        result.isDone()
        result.get().data == ['hello': 'world', 'hello2': null]
        result.get().getErrors().size() == 1
        result.get().getErrors().get(0).errorType == ErrorType.DataFetchingException

    }

    def "exception in instrumentation while combining data"() {
        GraphQLSchema schema = schema(
                { env -> CompletableFuture.completedFuture("world") },
                { env -> CompletableFuture.completedFuture("world2") }
        )
        String query = "{hello, hello2}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionStepInfo.newExecutionStepInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .graphQLContext(graphqlContextMock)
                .locale(Locale.getDefault())
                .instrumentation(new SimplePerformantInstrumentation() {

                    @Override
                    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState state) {
                        return new ExecutionStrategyInstrumentationContext() {

                            @Override
                            void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                                throw new RuntimeException("Exception raised from instrumentation")
                            }

                            @Override
                            void onDispatched() {
                            }

                            @Override
                            void onCompleted(ExecutionResult result, Throwable t) {
                            }
                        }
                    }
                })
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField([new Field('hello')]), 'hello2': mergedField([new Field('hello2')])]))
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)

        then: "result should be completed"
        result.isCompletedExceptionally()

        when:
        result.join()

        then: "exceptions thrown from the instrumentation should be bubbled up"
        def ex = thrown(CompletionException)
        ex.cause.message == "Exception raised from instrumentation"
    }


}

class AsyncExecutionStrategyTestWithIncrementalSupport extends AsyncExecutionStrategyTest {
    static {
        incrementalSupport = true
    }
}

class AsyncExecutionStrategyTestNoIncrementalSupport extends AsyncExecutionStrategyTest {
    static {
        incrementalSupport = false
    }
}
