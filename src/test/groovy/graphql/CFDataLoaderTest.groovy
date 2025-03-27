package graphql

import graphql.execution.CF
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput

class CFDataLoaderTest extends Specification {


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
            return CF.newDataLoaderCF(env, "name", "Key1").thenCompose {
                result ->
                    {
                        return CF.newDataLoaderCF(env, "name", result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return CF.newDataLoaderCF(env, "name", "Key2").thenCompose {
                result ->
                    {
                        return CF.newDataLoaderCF(env, "name", result)
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

    def "chained data loaders with second one delayed"() {
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
            return CF.newDataLoaderCF(env, "name", "Key1").thenCompose {
                result ->
                    {
                        return CF.newDataLoaderCF(env, "name", result)
                    }
            }
        } as DataFetcher

        def df2 = { env ->
            return CF.newDataLoaderCF(env, "name", "Key2").thenCompose {
                result ->
                    {
                        return CF.newDataLoaderCF(env, "name", result)
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


}
