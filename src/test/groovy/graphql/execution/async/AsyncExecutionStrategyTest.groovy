package graphql.execution.async

import graphql.ExceptionWhileDataFetching
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategy
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.*
import spock.lang.Ignore
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

    def "produces the correct results"() {
        given:
        def strategy = AsyncExecutionStrategy.serial()

        def composite = newObject()
          .name('composite')
          .field(field('field', GraphQLString, { env ->
            env.getSource()['field']
        }))
          .build()

        def parentType = newObject()
          .name('object')
          .field(field('field', GraphQLString, { completedFuture('string') }))
          .field(field('listOfScalars', new GraphQLList(GraphQLString), { completedFuture(['a']) }))
          .field(field('listOfScalars2', new GraphQLList(GraphQLString), { [completedFuture('a')] }))
          .field(field('listOfComposite', new GraphQLList(composite), { [[field: 'value']] }))
          .field(field('listOfFutureComposite', new GraphQLList(composite), { [completedFuture([field: 'value'])] }))
          .field(field('completesExceptionally', GraphQLString, { exceptionallyCompletedFuture(exception) }))
          .field(field('throwsException', GraphQLString, { throw exception }))
          .build()

        executionContext = buildExecutionContext(strategy, parentType)

//        def fields = [
//          listOfFutureComposite: [new Field('listOfFutureComposite', new SelectionSet([new Field('field')]))],
//        ]

        def fields = [
          listOfScalars2        : [new Field('listOfScalars2')],
          field                 : [new Field('field')],
          listOfScalars         : [new Field('listOfScalars')],
          listOfComposite       : [new Field('listOfComposite', new SelectionSet([new Field('field')]))],
          listOfFutureComposite : [new Field('listOfFutureComposite', new SelectionSet([new Field('field')]))],
          completesExceptionally: [new Field('completesExceptionally')],
          throwsException       : [new Field('throwsException')]
        ];

        when:
        def result = strategy.execute(executionContext, parentType, null, fields)

        then:
        def conds = new AsyncConditions(1)

        result.getData().thenAccept({ data ->
            conds.evaluate {
//                assert data == [
//                  listOfFutureComposite: [[field: 'value']],
//                ]
                assert data == [
                  listOfScalars2        : ['a'],
                  field                 : 'string',
                  listOfScalars         : ['a'],
                  listOfComposite       : [[field: 'value']],
                  listOfFutureComposite : [[field: 'value']],
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

    @Ignore
    def "in the correct order"() {

    }

    public <T> CompletionStage<T> exceptionallyCompletedFuture(Throwable exception) {
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
