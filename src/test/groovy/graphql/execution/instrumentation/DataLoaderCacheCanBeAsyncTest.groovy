package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import org.dataloader.ValueCache
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class DataLoaderCacheCanBeAsyncTest extends Specification {

    def sdl = """
        type Query {
            user(id : ID) : User
        }
        
        type User {
            id : ID
            name : String
        }
    """

    static class CustomValueCache implements ValueCache<String, Object> {
        Map<String, Object> store = [:]

        int getRandomNumber(int min, int max) {
            Random random = new Random()
            return random.nextInt(max - min) + min
        }

        @Override
        CompletableFuture get(String key) {
            return CompletableFuture.supplyAsync({
                Thread.sleep(getRandomNumber(100, 500))
                if (store.containsKey(key)) {
                    return store.get(key)
                }
                throw new RuntimeException("Key Missing")
            })
        }

        @Override
        CompletableFuture set(String key, Object value) {
            return CompletableFuture.supplyAsync({
                Thread.sleep(getRandomNumber(100, 500))
                store.put(key, value)
                return value
            })
        }

        @Override
        CompletableFuture<Void> delete(String key) {
            return CompletableFuture.completedFuture(null)
        }

        @Override
        CompletableFuture<Void> clear() {
            return CompletableFuture.completedFuture(null)
        }
    }

    DataLoaderRegistry registry
    GraphQL graphQL

    void setup() {

        BatchLoader userBatchLoader = { List<String> keys ->
            return CompletableFuture.supplyAsync({ ->
                Thread.sleep(100)
                def users = []
                for (String k : keys) {
                    users.add([id: k, name: k + "Name"])
                }
                users
            })
        }


        def valueCache = new CustomValueCache()
        valueCache.store.put("a", [id: "cachedA", name: "cachedAName"])

        DataLoaderOptions options = DataLoaderOptions.newOptions().setValueCache(valueCache).setCachingEnabled(true)
        DataLoader userDataLoader = DataLoaderFactory.newDataLoader(userBatchLoader, options)

        registry = DataLoaderRegistry.newRegistry()
                .register("users", userDataLoader)
                .build()

        DataFetcher userDF = { DataFetchingEnvironment env ->
            def id = env.getArgument("id")
            def loader = env.getDataLoader("users")
            return loader.load(id)
        }

        def schema = TestUtil.schema(sdl, [Query: [user: userDF]])
        graphQL = GraphQL.newGraphQL(schema).build()

    }

    def "can execute data loader calls"() {
        def query = '''
            query {
                a: user(id : "a") {
                    id name
                }
                b: user(id : "b") {
                    id name
                }
                c: user(id : "c") {
                    id name
                }
            }
        '''
        def executionInput = ExecutionInput.newExecutionInput(query).dataLoaderRegistry(registry).build()

        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        er.data == [a: [id: "cachedA", name: "cachedAName"],
                    b: [id: "b", name: "bName"],
                    c: [id: "c", name: "cName"],
        ]
    }
}
