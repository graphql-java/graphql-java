package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.RepeatUntilFailure
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class Issue1178DataLoaderDispatchTest extends Specification {


    @RepeatUntilFailure(maxAttempts = 100, ignoreRest = false)
    def "shouldn't dispatch twice in multithreaded env"() {
        setup:
        def sdl = """
        type Todo {
           id: ID!
           related: Todo
           related2: Todo
        }

        type Query {
           getTodos: [Todo]
        }

        schema {
           query: Query
        }"""

        def executor = Executors.newFixedThreadPool(5)

        def dataLoader = DataLoaderFactory.newDataLoader(new BatchLoader<Object, Object>() {
            @Override
            CompletionStage<List<Object>> load(List<Object> keys) {
                return CompletableFuture.supplyAsync({
                    return keys.collect({ [id: 'r' + it] })
                }, executor)
            }
        })
        def dataLoader2 = DataLoaderFactory.newDataLoader(new BatchLoader<Object, Object>() {
            @Override
            CompletionStage<List<Object>> load(List<Object> keys) {
                return CompletableFuture.supplyAsync({
                    return keys.collect({ [id: 'r' + it] })
                }, executor)
            }
        })

        def dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("todo.related", dataLoader)
        dataLoaderRegistry.register("todo.related2", dataLoader2)

        def relatedDf = new MyDataFetcher("todo.related")
        def relatedDf2 = new MyDataFetcher("todo.related2")

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("getTodos", new StaticDataFetcher([[id: '1'], [id: '2'], [id: '3'], [id: '4'], [id: '5']])))
                .type(newTypeWiring("Todo")
                        .dataFetcher("related", relatedDf)
                        .dataFetcher("related2", relatedDf2))
                .build()


        when:
        def graphql = TestUtil.graphQL(sdl, wiring)
                .build()

        then: "execution shouldn't error"
        def ei = ExecutionInput.newExecutionInput()
                .graphQLContext([(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): enableDataLoaderChaining])
                .dataLoaderRegistry(dataLoaderRegistry)
                .query("""
                query { 
                    getTodos { __typename id 
                        related { id __typename 
                            related { id __typename 
                                related2 { id __typename 
                                    related2 { id __typename 
                                        related { id __typename }
                                    }
                                }
                            } 
                        }
                        related2 { id __typename 
                            related2 { id __typename 
                                related { id __typename 
                                    related { id __typename 
                                        related2 { id __typename 
                                            related2 { id __typename 
                                                related { id __typename }
                                                related2 { id __typename 
                                                    related2 { id __typename 
                                                        related { id __typename }
                                                    }
                                                }
                                            }
                                            related { id __typename }
                                        }
                                    } 
                                }
                            } 
                        }
                    } 
                }""").build()
        def resultCF = graphql.executeAsync(ei)
        Awaitility.await().until { resultCF.isDone() }
        assert resultCF.get().errors.empty
        where:
        enableDataLoaderChaining << [true, false]

    }

    static class MyDataFetcher implements DataFetcher<CompletableFuture<Object>> {

        private final String name

        MyDataFetcher(String name) {
            this.name = name
        }

        @Override
        CompletableFuture<Object> get(DataFetchingEnvironment environment) {
            def todo = environment.source as Map
            return environment.getDataLoader(name).load(todo['id'])
        }
    }
}
