package graphql.execution

import graphql.ErrorType
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

class AsyncExecutionStrategyTest extends Specification {

    GraphQLSchema schema(DataFetcher dataFetcher1, DataFetcher dataFetcher2) {
        GraphQLFieldDefinition.Builder fieldDefinition = newFieldDefinition()
                .name("hello")
                .type(GraphQLString)
                .dataFetcher(dataFetcher1)
        GraphQLFieldDefinition.Builder fieldDefinition2 = newFieldDefinition()
                .name("hello2")
                .type(GraphQLString)
                .dataFetcher(dataFetcher2)

        GraphQLSchema schema = newSchema().query(
                newObject()
                        .name("RootQueryType")
                        .field(fieldDefinition)
                        .field(fieldDefinition2)
                        .build()
        ).build()
        schema
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
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')]])
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
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')]])
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
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')]])
                .build()

        AsyncExecutionStrategy asyncExecutionStrategy = new AsyncExecutionStrategy()
        when:
        def result = asyncExecutionStrategy.execute(executionContext, executionStrategyParameters)


        then:
        !result.isDone()
        Thread.sleep(200)
        result.isDone()
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
                .fields(['hello': [new Field('hello')], 'hello2': [new Field('hello2')]])
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
}
