package graphql

import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring
import static graphql.ExecutionInput.newExecutionInput

class Issue2068 extends Specification {
    def "deadlock attempt"() {
        setup:
        def sdl = """
        type Nation {
            name: String
        }
        
        type Toy {
            name: String
        }
        
        type Owner {
            nation: Nation
        }
        
        type Cat {
            toys: [Toy]
        }
        
        type Dog {
            owner: Owner
        }
        
        type Pets {
            cats: [Cat]
            dogs: [Dog]
        }
        
        type Query {
            pets: Pets
        }
        """

        def cats = [['id': "cat-1"]]
        def dogs = [['id': "dog-1"]]

        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("resolver-chain-thread-%d").build()
        def executor = new ThreadPoolExecutor(15, 15, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy())

        DataFetcher nationsDf = { env -> env.getDataLoader("owner.nation").load(env) }
        DataFetcher ownersDf = { env -> env.getDataLoader("dog.owner").load(env) }

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("pets", new StaticDataFetcher(['cats': cats, 'dogs': dogs])))
                .type(newTypeWiring("Cat")
                        .dataFetcher("toys", new StaticDataFetcher(new List<Object>() {
                            @Override
                            int size() {
                                return 1
                            }

                            @Override
                            boolean isEmpty() {
                                return false
                            }

                            @Override
                            boolean contains(Object o) {
                                return false
                            }

                            @Override
                            Iterator iterator() {
                                throw new RuntimeException()
                            }

                            @Override
                            Object[] toArray() {
                                return new Object[0]
                            }

                            @Override
                            Object[] toArray(Object[] a) {
                                return null
                            }

                            @Override
                            boolean add(Object o) {
                                return false
                            }

                            @Override
                            boolean remove(Object o) {
                                return false
                            }

                            @Override
                            boolean containsAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean addAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean addAll(int index, Collection c) {
                                return false
                            }

                            @Override
                            boolean removeAll(Collection c) {
                                return false
                            }

                            @Override
                            boolean retainAll(Collection c) {
                                return false
                            }

                            @Override
                            void clear() {

                            }

                            @Override
                            Object get(int index) {
                                return null
                            }

                            @Override
                            Object set(int index, Object element) {
                                return null
                            }

                            @Override
                            void add(int index, Object element) {

                            }

                            @Override
                            Object remove(int index) {
                                return null
                            }

                            @Override
                            int indexOf(Object o) {
                                return 0
                            }

                            @Override
                            int lastIndexOf(Object o) {
                                return 0
                            }

                            @Override
                            ListIterator listIterator() {
                                return null
                            }

                            @Override
                            ListIterator listIterator(int index) {
                                return null
                            }

                            @Override
                            List subList(int fromIndex, int toIndex) {
                                return null
                            }
                        })))
                .type(newTypeWiring("Dog")
                        .dataFetcher("owner", ownersDf))
                .type(newTypeWiring("Owner")
                        .dataFetcher("nation", nationsDf))
                .build()

        def schema = TestUtil.schema(sdl, wiring)

        when:
        def graphql = GraphQL.newGraphQL(schema)
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()
        DataLoaderRegistry dataLoaderRegistry = mkNewDataLoaderRegistry(executor)

        graphql.execute(newExecutionInput()
                .dataLoaderRegistry(dataLoaderRegistry)
                .query("""
                query LoadPets {
                      pets {
                        cats {
                          toys {
                            name
                          }
                        }
                        dogs {
                          owner {
                            nation {
                              name
                            }
                          }
                        }
                      }
                    }
                    """)
                .build())

        then: "execution shouldn't hang"
        // wait for each future to complete and grab the results
        thrown(RuntimeException)
    }

    private static DataLoaderRegistry mkNewDataLoaderRegistry(executor) {
        def dataLoaderNations = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def nations = []
                    for (int i = 1; i <= 1; i++) {
                        nations.add(['id': "nation-$i", 'name': "nation-$i"])
                    }
                    return nations
                }, executor) as CompletionStage<List<List<Object>>>
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderOwners = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def owners = []
                    for (int i = 1; i <= 1; i++) {
                        owners.add(['id': "owner-$i"])
                    }
                    return owners
                }, executor) as CompletionStage<List<List<Object>>>
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("dog.owner", dataLoaderOwners)
        dataLoaderRegistry.register("owner.nation", dataLoaderNations)
        dataLoaderRegistry
    }
}
