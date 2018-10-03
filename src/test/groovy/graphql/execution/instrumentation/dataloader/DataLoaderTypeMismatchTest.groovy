package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataLoaderTypeMismatchTest extends Specification {

    def "when actual field value return type is different from expected return type, then it should not hang execution"() {
        setup:
        def sdl = """
        type Todo {
           id: ID!
        }

        type Query {
           getTodos: [Todo]
        }

        schema {
           query: Query
        }"""

        def typeDefinitionRegistry = new SchemaParser().parse(sdl)

        def dataLoader = new DataLoader<Object, Object>(new BatchLoader<Object, Object>() {
            @Override
            CompletionStage<List<Object>> load(List<Object> keys) {
                return CompletableFuture.completedFuture([
                        [a: "map instead of a list of todos"]
                ])
            }
        })
        def dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("getTodos", dataLoader)

        def todosDef = new DataFetcher<CompletableFuture<Object>>() {
            @Override
            CompletableFuture<Object> get(DataFetchingEnvironment environment) {
                return dataLoader.load(environment)
            }
        }

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                    .dataFetcher("getTodos", todosDef))
                .build()

        def schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, wiring)

        def graphql = GraphQL.newGraphQL(schema)
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()

        when:
        def result = graphql.execute(ExecutionInput.newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query("query { getTodos { id } }").build())

        then: "execution shouldn't hang"
        !result.errors.empty
        result.errors[0].message == "Can't resolve value (/getTodos) : type mismatch error, expected type LIST"
    }
}
