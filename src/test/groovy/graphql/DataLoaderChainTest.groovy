package graphql


import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput

class DataLoaderChainTest extends Specification {


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
            return CompletableFuture.supplyAsync {
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
            return env.getDataLoaderChain().load("name", "Key1").thenCompose {
                result ->
                    {
                        return env.getDataLoaderChain().load("name", result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoaderChain().load("name", "Key2").thenCompose {
                result ->
                    {
                        return env.getDataLoaderChain().load("name", result)
                    }
            }
        } as DataFetcher


        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [dogName: "Luna", catName: "Tiger"]
        batchLoadCalls == 2
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
            return CompletableFuture.supplyAsync {
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
            return CompletableFuture.supplyAsync {
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
            return env.getDataLoaderChain().load("dl1", "start").thenCompose {
                firstDLResult ->

                    def otherCF1 = env.getDataLoaderChain().supplyAsync {
                        Thread.sleep(1000)
                        return "otherCF1"
                    }
                    def otherCF2 = env.getDataLoaderChain().supplyAsync {
                        Thread.sleep(1000)
                        return "otherCF2"
                    }

                    def secondDL = env.getDataLoaderChain().load("dl2", firstDLResult).thenApply {
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
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()

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
            return CompletableFuture.supplyAsync {
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
            return env.getDataLoaderChain().load("name", "Luna0").thenCompose {
                result ->
                    {
                        return env.getDataLoaderChain().supplyAsync {
                            Thread.sleep(1000)
                            return "foo"
                        }.thenCompose {
                            return env.getDataLoaderChain().load("name", result)
                        }
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return env.getDataLoaderChain().load("name", "Tiger0").thenCompose {
                result ->
                    {
                        return env.getDataLoaderChain().load("name", result)
                    }
            }
        } as DataFetcher


        def fetchers = ["Query": ["dogName": df1, "catName": df2]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ dogName catName } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [dogName: "Luna2", catName: "Tiger2"]
        batchLoadCalls == 3
    }

    def "chained data loaders with two isolated data loaders"() {
        // TODO: this test is naturally flaky, because there is no guarantee that the Thread.sleep(1000) finish close
        // enough time wise to be batched together
        given:
        def sdl = '''

        type Query {
          foo: String
         bar: String
        }
        '''
        int batchLoadCalls = 0
        BatchLoader<String, String> batchLoader = { keys ->
            return CompletableFuture.supplyAsync {
                batchLoadCalls++
                Thread.sleep(250)
                println "BatchLoader called with keys: $keys"
                return keys;
            }
        }

        DataLoader<String, String> nameDataLoader = DataLoaderFactory.newDataLoader(batchLoader);

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry();
        dataLoaderRegistry.register("dl", nameDataLoader);

        def fooDF = { env ->
            return env.getDataLoaderChain().supplyAsync {
                Thread.sleep(1000)
                return "fooFirstValue"
            }.thenCompose {
                return env.getDataLoaderChain().load("dl", it)
            }
        } as DataFetcher

        def barDF = { env ->
            return env.getDataLoaderChain().supplyAsync {
                Thread.sleep(1000)
                return "barFirstValue"
            }.thenCompose {
                return env.getDataLoaderChain().load("dl", it)
            }
        } as DataFetcher


        def fetchers = ["Query": ["foo": fooDF, "bar": barDF]]
        def schema = TestUtil.schema(sdl, fetchers)
        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = "{ foo bar } "
        def ei = newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.data == [foo: "fooFirstValue", bar: "barFirstValue"]
        batchLoadCalls == 1
    }


}
