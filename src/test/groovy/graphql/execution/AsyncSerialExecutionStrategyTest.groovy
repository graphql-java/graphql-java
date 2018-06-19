package graphql.execution

import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class AsyncSerialExecutionStrategyTest extends Specification {
    GraphQLSchema schema(DataFetcher dataFetcher1, DataFetcher dataFetcher2, DataFetcher dataFetcher3) {
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .dataFetcher(dataFetcher1)
        GraphQLFieldDefinition.Builder fieldDefinition2 = newFieldDefinition()
                .name("hello2")
                .type(GraphQLString)
                .dataFetcher(dataFetcher2)
        GraphQLFieldDefinition.Builder fieldDefinition3 = newFieldDefinition()
                .name("hello3")
                .type(GraphQLString)
                .dataFetcher(dataFetcher3)

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .field(fieldDefinition2)
                        .field(fieldDefinition3)
                        .build()
        ).build()
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

        def typeInfo = ExecutionTypeInfo.newTypeInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .instrumentation(SimpleInstrumentation.INSTANCE)
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .typeInfo(typeInfo)
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')], 'hello3': [new Field('hello3')]])
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
        def df1 = Mock(DataFetcher)
        def cf1 = new CompletableFuture()

        def df2 = Mock(DataFetcher)
        def cf2 = new CompletableFuture()

        def df3 = Mock(DataFetcher)
        def cf3 = new CompletableFuture()

        GraphQLSchema schema = schema(df1, df2, df3)
        String query = "{hello, hello2, hello3}"
        def document = new Parser().parseDocument(query)
        def operation = document.definitions[0] as OperationDefinition

        def typeInfo = ExecutionTypeInfo.newTypeInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .operationDefinition(operation)
                .instrumentation(SimpleInstrumentation.INSTANCE)
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .typeInfo(typeInfo)
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')], 'hello3': [new Field('hello3')]])
                .build()

        AsyncSerialExecutionStrategy strategy = new AsyncSerialExecutionStrategy()
        when:
        def result = strategy.execute(executionContext, executionStrategyParameters)


        then:
        !result.isDone()
        1 * df1.get(_) >> cf1
        0 * df2.get(_) >> cf2
        0 * df3.get(_) >> cf3

        when:
        cf1.complete("world1")

        then:
        !result.isDone()
        0 * df1.get(_) >> cf1
        1 * df2.get(_) >> cf2
        0 * df3.get(_) >> cf3

        when:
        cf2.complete("world2")

        then:
        !result.isDone()
        0 * df1.get(_) >> cf1
        0 * df2.get(_) >> cf2
        1 * df3.get(_) >> cf3

        when:
        cf3.complete("world3")

        then:
        0 * df1.get(_) >> cf1
        0 * df2.get(_) >> cf2
        0 * df3.get(_) >> cf3
        result.isDone()
        result.get().data == ['hello': 'world1', 'hello2': 'world2', 'hello3': 'world3']
    }
}
