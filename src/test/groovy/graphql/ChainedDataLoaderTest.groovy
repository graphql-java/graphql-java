package graphql

import graphql.execution.ExecutionId
import graphql.execution.instrumentation.dataloader.DelayedDataLoaderDispatcherExecutorFactory
import graphql.execution.instrumentation.dataloader.DispatchingContextKeys
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static graphql.ExecutionInput.newExecutionInput
import static java.util.concurrent.CompletableFuture.supplyAsync

class ChainedDataLoaderTest extends Specification {


    def "chained data loaders"() {
        given:
        def sdl = '''

        type Query {
          dogName: String
          catName: String
        }
        '''
        int batchLoadCalls = 0
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls++
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                assert keys.size() == 2
                return ["Luna", "Tiger"]
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def df1 = { env ->
            return env.getDataLoader("name").load("Key1").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoader("name").load("Key2").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher


        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [dogName: "Luna", catName: "Tiger"]
        batchLoadCalls == 2
    }

    def "parallel different data loaders"() {
        given:
        def sdl = '''

        type Query {
          hello: String
          helloDelayed: String
        }
        '''
        AtomicInteger batchLoadCalls = new AtomicInteger()
        BatchLoader<String, String> batchLoader1 = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader 1 called with keys: $keys"
                assert keys.size() == 1
                return ["Luna" + keys[0]]
            }
        }

        BatchLoader<String, String> batchLoader2 = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader 2 called with keys: $keys"
                assert keys.size() == 1
                return ["Skipper" + keys[0]]
            }
        }
        BatchLoader<String, String> batchLoader3 = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader 3 called with keys: $keys"
                assert keys.size() == 1
                return ["friends" + keys[0]]
            }
        }


        DataLoader<String, String> dl1 = DataLoaderFactory.newDataLoader(batchLoader1);
        DataLoader<String, String> dl2 = DataLoaderFactory.newDataLoader(batchLoader2);
        DataLoader<String, String> dl3 = DataLoaderFactory.newDataLoader(batchLoader3);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("dl1", dl1);
        dataLoaderRegistry.register("dl2", dl2);
        dataLoaderRegistry.register("dl3", dl3);

        def df = { env ->
            def cf1 = env.getDataLoader("dl1").load("key1")
            def cf2 = env.getDataLoader("dl2").load("key2")
            return cf1.thenCombine(cf2, { result1, result2 ->
                return result1 + result2
            }).thenCompose {
                return env.getDataLoader("dl3").load(it)
            }
        } as DataFetcher

        def dfDelayed = { env ->
            return supplyAsync {
                Thread.sleep(2000)
            }.thenCompose {
                def cf1 = env.getDataLoader("dl1").load("key1-delayed")
                def cf2 = env.getDataLoader("dl2").load("key2-delayed")
                return cf1.thenCombine(cf2, { result1, result2 ->
                    return result1 + result2
                }).thenCompose {
                    return env.getDataLoader("dl3").load(it)
                }
            }
        } as DataFetcher


        def fetchers = [Query: [hello: df, helloDelayed: dfDelayed]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ hello helloDelayed} "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [hello: "friendsLunakey1Skipperkey2", helloDelayed: "friendsLunakey1-delayedSkipperkey2-delayed"]
        batchLoadCalls.get() == 6
    }


    def "more complicated chained data loader for one DF"() {
        given:
        def sdl = '''

        type Query {
           foo: String
        }
        '''
        int batchLoadCalls1 = 0
        BatchLoader<String, String> batchLoader1 = { keys ->
            return supplyAsync {
                batchLoadCalls1++
                Thread.sleep(250)
                println "BatchLoader1 called with keys: $keys"
                return keys.collect { String key ->
                    key + "-batchloader1"
                }
            }
        }
        int batchLoadCalls2 = 0
        BatchLoader<String, String> batchLoader2 = { keys ->
            return supplyAsync {
                batchLoadCalls2++
                Thread.sleep(250)
                println "BatchLoader2 called with keys: $keys"
                return keys.collect { String key ->
                    key + "-batchloader2"
                }
            }
        }


        DataLoader<String, String> dl1 = DataLoaderFactory.newDataLoader(batchLoader1);
        DataLoader<String, String> dl2 = DataLoaderFactory.newDataLoader(batchLoader2);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("dl1", dl1);
        dataLoaderRegistry.register("dl2", dl2);

        def df = { env ->
            return env.getDataLoader("dl1").load("start").thenCompose {
                firstDLResult ->

                    def otherCF1 = supplyAsync {
                        Thread.sleep(1000)
                        return "otherCF1"
                    }
                    def otherCF2 = supplyAsync {
                        Thread.sleep(1000)
                        return "otherCF2"
                    }

                    def secondDL = env.getDataLoader("dl2").load(firstDLResult).thenApply {
                        secondDLResult ->
                            return secondDLResult + "-apply"
                    }
                    return otherCF1.thenCompose {
                        otherCF1Result ->
                            otherCF2.thenCompose {
                                otherCF2Result ->
                                    secondDL.thenApply {
                                        secondDLResult ->
                                            return firstDLResult + "-" + otherCF1Result + "-" + otherCF2Result + "-" + secondDLResult
                                    }
                            }
                    }

            }
        } as DataFetcher


        def fetchers = ["Query": ["foo": df]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ foo } "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [foo: "start-batchloader1-otherCF1-otherCF2-start-batchloader1-batchloader2-apply"]
        batchLoadCalls1 == 1
        batchLoadCalls2 == 1
    }


    def "chained data loaders with an isolated data loader"() {
        given:
        def sdl = '''

        type Query {
          dogName: String
          catName: String
        }
        '''
        int batchLoadCalls = 0
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls++
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                return keys.collect { String key ->
                    key.substring(0, key.length() - 1) + (Integer.parseInt(key.substring(key.length() - 1, key.length())) + 1)
                }
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def df1 = { env ->
            return env.getDataLoader("name").load("Luna0").thenCompose {
                result ->
                    {
                        return supplyAsync {
                            Thread.sleep(1000)
                            return "foo"
                        }.thenCompose {
                            return env.getDataLoader("name").load(result)
                        }
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoader("name").load("Tiger0").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher


        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [dogName: "Luna2", catName: "Tiger2"]
        batchLoadCalls == 3
    }

    def "chained data loaders with two isolated data loaders"() {
        given:
        def sdl = '''

        type Query {
          foo: String
         bar: String
        }
        '''
        AtomicInteger batchLoadCalls = new AtomicInteger()
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls.incrementAndGet()
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                return keys;
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("dl", nameDataLoader);

        def fooDF = { env ->
            return supplyAsync {
                Thread.sleep(1000)
                return "fooFirstValue"
            }.thenCompose {
                return env.getDataLoader("dl").load(it)
            }
        } as DataFetcher

        def barDF = { env ->
            return supplyAsync {
                Thread.sleep(1000)
                return "barFirstValue"
            }.thenCompose {
                return env.getDataLoader("dl").load(it)
            }
        } as DataFetcher


        def fetchers = ["Query": ["foo": fooDF, "bar": barDF]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ foo bar } "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()

        // make the window to 50ms
        ei.getGraphQLContext().put(DispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS, 1_000_000L * 250)

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [foo: "fooFirstValue", bar: "barFirstValue"]
        batchLoadCalls.get() == 1
    }

    def "executor for delayed dispatching can be configured"() {
        given:
        def sdl = '''

        type Query {
          foo: String
         bar: String
        }
        '''
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                Thread.sleep(250)
                return keys;
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("dl", nameDataLoader);

        def fooDF = { env ->
            return supplyAsync {
                Thread.sleep(1000)
                return "fooFirstValue"
            }.thenCompose {
                return env.getDataLoader("dl").load(it)
            }
        } as DataFetcher


        def fetchers = ["Query": ["foo": fooDF]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ foo } "
        def ei = newExecutionInput(query).graphQLContext([(DispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING): true]).dataLoaderRegistry(dataLoaderRegistry).build()


        ScheduledExecutorService scheduledExecutorService = Mock()
        ei.getGraphQLContext().put(DispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY, new DelayedDataLoaderDispatcherExecutorFactory() {
            @Override
            ScheduledExecutorService createExecutor(ExecutionId executionId, GraphQLContext graphQLContext) {
                return scheduledExecutorService
            }
        })


        when:
        def er = graphQL.execute(ei)

        then:
        er.data == [foo: "fooFirstValue"]
        1 * scheduledExecutorService.schedule(_ as Runnable, _ as Long, _ as TimeUnit) >> { Runnable runnable, Long delay, TimeUnit timeUnit ->
            return Executors.newSingleThreadScheduledExecutor().schedule(runnable, delay, timeUnit)
        }

    }

    def "handling of chained DataLoaders is disabled by default"() {
        given:
        def sdl = '''

        type Query {
          dogName: String
          catName: String
        }
        '''
        int batchLoadCalls = 0
        BatchLoader<String, String> batchLoader = { keys ->
            return supplyAsync {
                batchLoadCalls++
                println "BatchLoader called with keys: $keys"
                assert keys.size() == 2
                return ["Luna", "Tiger"]
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("name", nameDataLoader);

        def df1 = { env ->
            return env.getDataLoader("name").load("Key1").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoader("name").load("Key2").thenCompose {
                result ->
                    {
                        return env.getDataLoader("name").load(result)
                    }
            }
        } as DataFetcher


        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()


        when:
        def er = graphQL.executeAsync(ei)
        Thread.sleep(1000)
        then:
        batchLoadCalls == 1
        !er.isDone()
    }


}
