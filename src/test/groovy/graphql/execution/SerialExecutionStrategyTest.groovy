package graphql.execution

import graphql.execution.instrumentation.NoOpInstrumentation
import graphql.language.Field
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

class SerialExecutionStrategyTest extends Specification {

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

        def typeInfo = ExecutionTypeInfo.newTypeInfo()
                .type(schema.getQueryType())
                .build()

        ExecutionContext executionContext = new ExecutionContextBuilder()
                .graphQLSchema(schema)
                .executionId(ExecutionId.generate())
                .document(document)
                .valuesResolver(new ValuesResolver())
                .instrumentation(NoOpInstrumentation.INSTANCE)
                .build()
        ExecutionStrategyParameters executionStrategyParameters = ExecutionStrategyParameters
                .newParameters()
                .typeInfo(typeInfo)
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')], 'hello3': [new Field('hello3')]])
                .build()

        SerialExecutionStrategy strategy = new SerialExecutionStrategy()
        when:
        def result = strategy.execute(executionContext, executionStrategyParameters)


        then:
        result.isDone()
        result.get().data == ['hello': 'world1', 'hello2': 'world2', 'hello3': 'world3']
    }
}
