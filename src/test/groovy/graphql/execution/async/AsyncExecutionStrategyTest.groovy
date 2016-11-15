package graphql.execution.async

import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategy
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.*
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import static com.spotify.futures.CompletableFutures.exceptionallyCompletedFuture
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static java.util.concurrent.CompletableFuture.completedFuture

class AsyncExecutionStrategyTest extends Specification {

    def strategy = AsyncExecutionStrategy.serial()
    def fields = [field: [new Field('field')]]

    def parentType, executionContext, result, actual

    @Unroll
    def "async field"() {
        given:
        parentType = buildParentType(type, fetcher)
        executionContext = buildExecutionContext(strategy, parentType)
        actual = strategy.execute(executionContext, parentType, null, fields);

        expect:
        actual.data.field == expected

        where:
        fetcher                                                           | type                                                    || expected
        { it -> null }                                                    | GraphQLString                                           || null
        { it -> 'a' }                                                     | GraphQLString                                           || 'a'
        { it -> 'a' }                                                     | new GraphQLNonNull(GraphQLString)                       || 'a'
        { it -> ['a'] }                                                   | new GraphQLList(GraphQLString)                          || ['a']
        { it -> ['a'] }                                                   | new GraphQLList(new GraphQLNonNull(GraphQLString))      || ['a']
        { it -> completedFuture(null) }                                   | GraphQLString                                           || null
        { it -> completedFuture('value') }                                | GraphQLString                                           || 'value'
        { it -> completedFuture('value') }                                | new GraphQLNonNull(GraphQLString)                       || 'value'
        { it -> completedFuture(['value']) }                              | new GraphQLList(new GraphQLNonNull(GraphQLString))      || ['value']
        { it -> throw new RuntimeException() }                            | GraphQLString                                           || null
        { it -> exceptionallyCompletedFuture(new RuntimeException()) }    | GraphQLString                                           || null
    }

    def "async obj"() {
        given:
        def type = new GraphQLList(newObject()
          .name('composite')
          .field(field('field', GraphQLString, { 'value' }))
          .build())
        strategy = AsyncExecutionStrategy.serial()
        parentType = buildParentType(type, { completedFuture([[field: 'value']]) })
        executionContext = buildExecutionContext(strategy, parentType)
        fields = [field: [new Field('field', new SelectionSet([new Field('field')]))]]

        when:
        actual = strategy.execute(executionContext, parentType, null, fields);

        then:
        actual.data == [field: [[field: 'value']]]

    }

    @Ignore
    def "fields execute in the correct order"() {

    }

    GraphQLFieldDefinition field(String name, GraphQLOutputType type, DataFetcher fetcher) {
        newFieldDefinition()
          .name(name)
          .type(type)
          .dataFetcher(fetcher)
          .build()
    }

    GraphQLObjectType buildParentType(GraphQLOutputType type, DataFetcher fetcher) {
        newObject()
          .name('object')
          .field(field('field', type, fetcher))
          .build()
    }

    ExecutionContext buildExecutionContext(ExecutionStrategy strategy, GraphQLObjectType parentType) {
        def executionContext = new ExecutionContext(
          newSchema().query(parentType).build(),
          strategy,
          null,
          null,
          null,
          null,
          null
        )
    }
}
