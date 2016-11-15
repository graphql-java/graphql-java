package graphql.execution.async

import graphql.ExceptionWhileDataFetching
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategy
import graphql.language.Field
import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static java.util.concurrent.CompletableFuture.completedFuture

class AsyncExecutionStrategyTest extends Specification {

    def exception = new RuntimeException();

    def executionContext

    def "executes"() {
        given:
        def strategy = AsyncExecutionStrategy.serial()

        def parentType = newObject()
          .name('object')
          .field(field('field', GraphQLString, { completedFuture('string') }))
          .field(field('completesExceptionally', GraphQLString, { exceptionally(exception) }))
          .field(field('throwsException', GraphQLString, { throw exception }))
          .build()

        executionContext = buildExecutionContext(strategy, parentType)

        def fields = [
          field                 : [new Field('field')],
          completesExceptionally: [new Field('completesExceptionally')],
          throwsException       : [new Field('throwsException')]
        ];


        when:
        def result = strategy.execute(executionContext, parentType, null, fields)

        then:
        def conds = new AsyncConditions(1)

        result.getData().thenAccept({ data ->
            conds.evaluate {
                assert data == [
                  field                 : 'string',
                  completesExceptionally: null,
                  throwsException       : null
                ]
                assert result.errors.size() == 2
                (0..1).each {
                    result.errors[it] instanceof ExceptionWhileDataFetching
                    result.errors[it].exception == exception
                }
            }
        })

        conds.await()
    }

    public <T> CompletionStage<T> exceptionally(Throwable exception) {
        def future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        future;
    }

    GraphQLFieldDefinition field(String name, GraphQLOutputType type, DataFetcher fetcher) {
        newFieldDefinition()
          .name(name)
          .type(type)
          .dataFetcher(fetcher)
          .build()
    }

    ExecutionContext buildExecutionContext(ExecutionStrategy strategy, GraphQLObjectType parentType) {
        executionContext = new ExecutionContext()
        executionContext.setGraphQLSchema(newSchema().query(parentType).build())
        executionContext.setQueryStrategy(strategy)
        executionContext
    }
}
