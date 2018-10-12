package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class Issue1178DataLoaderDispatchTest extends Specification {

    public static final int NUM_OF_REPS = 100

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

        def dataLoader = new DataLoader<Object, Object>(new BatchLoader<Object, Object>() {
            @Override
            CompletionStage<List<Object>> load(List<Object> keys) {
                return CompletableFuture.supplyAsync({
                    return keys.collect({ [id: 'r' + it] })
                }, executor)
            }
        })
        def dataLoader2 = new DataLoader<Object, Object>(new BatchLoader<Object, Object>() {
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

        def relatedDf = new MyDataFetcher(dataLoader)
        def relatedDf2 = new MyDataFetcher(dataLoader2)

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                .dataFetcher("getTodos", new StaticDataFetcher([[id: '1'], [id: '2'], [id: '3'], [id: '4'], [id: '5']])))
                .type(newTypeWiring("Todo")
                .dataFetcher("related", relatedDf)
                .dataFetcher("related2", relatedDf2))
                .build()


        when:
        def graphql = TestUtil.graphQL(sdl, wiring)
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()

        then: "execution shouldn't error"
        for (int i = 0; i < NUM_OF_REPS; i++) {
            def result = graphql.execute(ExecutionInput.newExecutionInput().dataLoaderRegistry(dataLoaderRegistry)
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
                }""").build())
            assert result.errors.empty
        }
    }

    static class MyDataFetcher implements DataFetcher<CompletableFuture<Object>> {

        private final DataLoader dataLoader

        MyDataFetcher(DataLoader dataLoader) {
            this.dataLoader = dataLoader
        }

        @Override
        CompletableFuture<Object> get(DataFetchingEnvironment environment) {
            def todo = environment.source as Map
            return dataLoader.load(todo['id'])
        }
    }
}
