package graphql

import graphql.schema.GraphQLSchema
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static java.util.concurrent.CompletableFuture.completedFuture

class ComposedDataLoadersTest extends Specification {

    private static final def QUERY = '{ level1field1 { level2field1 level2field2 } }'

    private static final BatchLoader<Integer, Integer> LOADER = v -> completedFuture(v.collect() {++it})

    private static final def REGISTRY = DataLoaderRegistry.newRegistry()
            .register('loader', DataLoaderFactory.newDataLoader(LOADER))
            .build()

    private static final def DUMMY_QUERY_SUBTYPE = newObject()
            .name('Subtype')
            .field(newFieldDefinition()
                    .name('level2field1')
                    .type(GraphQLString)
                    .dataFetcher(environment -> {
                        DataLoader<Integer, Integer> loader = environment.getDataLoader('loader')
                        loader.load(1).thenCompose(loader::load)
                    }))
            .field(newFieldDefinition()
                    .name('level2field2')
                    .type(GraphQLInt)
                    .dataFetcher(environment -> {
                        DataLoader<Integer, Integer> loader = environment.getDataLoader('loader')
                        loader.load(5).thenCompose(loader::load)
                    }))
            .build()

    private static final def DUMMY_QUERY_TYPE = newObject()
            .name('Query')
            .field(newFieldDefinition()
                    .name('level1field1')
                    .type(DUMMY_QUERY_SUBTYPE)
                    .dataFetcher(environment -> {
                        DataLoader<Integer, Integer> loader = environment.getDataLoader('loader')
                        loader.load(11).thenCompose(loader::load).thenApply {[:]}
                    })
            .build())

    def 'composed data loaders must complete to finish field fetching level'() {
        given:
        def graphQL = GraphQL.newGraphQL(GraphQLSchema.newSchema().query(DUMMY_QUERY_TYPE).build()).build()
        when:
        def result = graphQL.execute(ExecutionInput.newExecutionInput(QUERY).dataLoaderRegistry(REGISTRY).build()).data
        then:
        result == [
                level1field1: [
                        level2field1: '3',
                        level2field2: 7
                ]
        ]
    }

}
