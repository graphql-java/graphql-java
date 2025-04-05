package graphql.execution

import graphql.EngineRunningState
import graphql.ExecutionInput
import graphql.GraphQLContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.LightDataFetcher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mergedField
import static graphql.TestUtil.mergedSelectionSet
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class AsyncSerialExecutionStrategyTest extends Specification {
    GraphQLSchema schema(DataFetcher dataFetcher1, DataFetcher dataFetcher2, DataFetcher dataFetcher3) {
        def queryName = "RootQueryType"
        def field1Name = "hello"
        def field2Name = "hello2"
        def field3Name = "hello3"

        GraphQLFieldDefinition.Builder fieldDefinition1 = newFieldDefinition()
                .name(field1Name)
                .type(GraphQLString)
        GraphQLFieldDefinition.Builder fieldDefinition2 = newFieldDefinition()
                .name(field2Name)
                .type(GraphQLString)
        GraphQLFieldDefinition.Builder fieldDefinition3 = newFieldDefinition()
                .name(field3Name)
                .type(GraphQLString)

        def field1Coordinates = FieldCoordinates.coordinates(queryName, field1Name)
        def field2Coordinates = FieldCoordinates.coordinates(queryName, field2Name)
        def field3Coordinates = FieldCoordinates.coordinates(queryName, field3Name)

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(field1Coordinates, dataFetcher1)
                .dataFetcher(field2Coordinates, dataFetcher2)
                .dataFetcher(field3Coordinates, dataFetcher3)
                .build()

        GraphQLSchema schema = newSchema()
                .codeRegistry(codeRegistry)
                .query(newObject()
                        .name(queryName)
                        .field(fieldDefinition1)
                        .field(fieldDefinition2)
                        .field(fieldDefinition3)
                        .build()
                )
                .build()

        schema
    }


    def "serial execution"() {
        given:
        def atomicInteger = new AtomicInteger()
        def reentrantLock = new ReentrantLock()
        GraphQLSchema schema = schema(
                { env ->
                    assert reentrantLock.tryLock()
                    def result = CompletableFuture.completedFuture("world" + (atomicInteger.incrementAndGet()))
                    reentrantLock.unlock()
                    result
                },
                { env ->
                    assert reentrantLock.tryLock()
                    def result = CompletableFuture.completedFuture("world" + (atomicInteger.incrementAndGet()))
                    reentrantLock.unlock()
                    result
                },
                { env ->
                    assert reentrantLock.tryLock()
                    def result = CompletableFuture.completedFuture("world" + (atomicInteger.incrementAndGet()))
                    reentrantLock.unlock()
                    result
                }
        )
        String query = "{hello, hello2, hello3}"
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
                .graphQLContext(GraphQLContext.getDefault())
                .executionInput(ExecutionInput.newExecutionInput("{}").build())
                .engineRunningState(new EngineRunningState())
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField(new Field('hello')), 'hello2': mergedField(new Field('hello2')), 'hello3': mergedField(new Field('hello3'))]))
                .build()

        AsyncSerialExecutionStrategy strategy = new AsyncSerialExecutionStrategy()
        when:
        def result = strategy.execute(executionContext, executionStrategyParameters)


        then:
        result.isDone()
        result.get().data == ['hello': 'world1', 'hello2': 'world2', 'hello3': 'world3']
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "async serial execution test"() {
        given:
        def df1 = Mock(LightDataFetcher)
        def cf1 = new CompletableFuture()

        def df2 = Mock(LightDataFetcher)
        def cf2 = new CompletableFuture()

        def df3 = Mock(LightDataFetcher)
        def cf3 = new CompletableFuture()

        GraphQLSchema schema = schema(df1, df2, df3)
        String query = "{hello, hello2, hello3}"
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
                .graphQLContext(GraphQLContext.getDefault())
                .executionInput(ExecutionInput.newExecutionInput("{}").build())
                .engineRunningState(new EngineRunningState())
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .executionStepInfo(typeInfo)
                .fields(mergedSelectionSet(['hello': mergedField(new Field('hello')), 'hello2': mergedField(new Field('hello2')), 'hello3': mergedField(new Field('hello3'))]))
                .build()

        AsyncSerialExecutionStrategy strategy = new AsyncSerialExecutionStrategy()
        when:
        def result = strategy.execute(executionContext, executionStrategyParameters)


        then:
        !result.isDone()
        1 * df1.get(_, _, _) >> cf1
        0 * df2.get(_, _, _) >> cf2
        0 * df3.get(_, _, _) >> cf3

        when:
        cf1.complete("world1")

        then:
        !result.isDone()
        0 * df1.get(_, _, _) >> cf1
        1 * df2.get(_, _, _) >> cf2
        0 * df3.get(_, _, _) >> cf3

        when:
        cf2.complete("world2")

        then:
        !result.isDone()
        0 * df1.get(_, _, _) >> cf1
        0 * df2.get(_, _, _) >> cf2
        1 * df3.get(_, _, _) >> cf3

        when:
        cf3.complete("world3")

        then:
        0 * df1.get(_, _, _) >> cf1
        0 * df2.get(_, _, _) >> cf2
        0 * df3.get(_, _, _) >> cf3
        result.isDone()
        result.get().data == ['hello': 'world1', 'hello2': 'world2', 'hello3': 'world3']
    }
}
