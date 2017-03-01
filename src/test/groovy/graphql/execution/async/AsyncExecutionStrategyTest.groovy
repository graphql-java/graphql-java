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

    @Unroll
    def "async field"() {
        given:
        def strategy = AsyncExecutionStrategy.serial()
        def parentType = buildParentType(type, fetcher)
        def executionContext = buildExecutionContext(strategy, parentType)
        def fields = [field: [new Field('field')]]

        when:
        def actual = strategy.execute(executionContext, parentType, null, fields);

        then:
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
          .name('Composite')
          .field(field('field', GraphQLString, { 'value' }))
          .build())
        def strategy = AsyncExecutionStrategy.serial()
        def parentType = buildParentType(type, { completedFuture([[field: 'value']]) })
        def executionContext = buildExecutionContext(strategy, parentType)
        def fields = [field: [new Field('field', new SelectionSet([new Field('field')]))]]

        when:
        def actual = strategy.execute(executionContext, parentType, null, fields);

        then:
        actual.data == [field: [[field: 'value']]]
    }

    // http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
    // - "Since Non-Null type fields cannot be null, field errors are propagated
    //   to be handled by the parent field. If the parent field may be null then
    //   it resolves to null, otherwise if it is a Non-Null type, the field error
    //   is further propagated to it’s parent field."
    // - "...only one error should be added to the errors list per field."

    def "null non-null field results in null nullable parent"() {
        given:
        def type = newObject()
          .name('ParentType')
          .field(newFieldDefinition()
              .name('field')
              .type(new GraphQLNonNull(GraphQLString))
              .dataFetcher({ null }))
          .build()
        def strategy = AsyncExecutionStrategy.serial()
        def executionContext = buildExecutionContext(strategy, type)
        def fields = [field: [new Field('field')]]

        when:
        def actual = strategy.execute(executionContext, type, [:], fields)

        then:
        actual.data == null
    }

    def "null non-null fields propagate to nearest nullable parent"() {
        given:
        def type = newObject()
            .name('ParentType')
            .field(newFieldDefinition()
                .name('nullableField')
                .type(newObject()
                    .name('ChildType')
                    .field(newFieldDefinition()
                        .name('nonNullField')
                        .type(new GraphQLNonNull(newObject()
                            .name('GrandChildType')
                            .field(newFieldDefinition()
                                .name('nonNullField')
                                .type(new GraphQLNonNull(GraphQLString))).build()))))
                .dataFetcher({[nonNullField: [:]]}))
            .build()
        def strategy = AsyncExecutionStrategy.serial()
        def executionContext = buildExecutionContext(strategy, type)
        def fields = [nullableField: [new Field('nullableField', new SelectionSet([new Field('nonNullField', new SelectionSet([new Field('nonNullField')]))]))]]

        when:
        def actual = strategy.execute(executionContext, type, [:], fields)

        then:
        actual.data == [nullableField: null]
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
          null,
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
