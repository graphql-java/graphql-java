package graphql

import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static graphql.TestUtil.schemaFile
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class BatchingInstrumentationWrongTypeTest extends Specification {

    BatchLoader<String, Object> quotesBatchLoader = new BatchLoader<String, Object>() {
        @Override
        CompletionStage<List<Object>> load(List<String> keys) {
            CompletableFuture.supplyAsync({
                return keys.stream().map({ ["quote": "I'm your father"] }).collect(Collectors.toList())
            })
        }

    }

    def quotesLoader = new DataLoader<String, Object>(quotesBatchLoader)

    DataFetcher quoteBatchingFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            quotesLoader.load(environment.arguments.ids)
        }
    }

    def starWarsWiring() {

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("QueryType")
                .dataFetchers(
                [
                        "allQuotes": quoteBatchingFetcher,
                ])).build()
        wiring
    }

    def schema = schemaFile("wrongTypeTest.graphqls", starWarsWiring())


    def query = """
        query {
            allQuotes(ids: ["1", "2"])
        }
        """

    def "basic batch loading is possible via instrumentation interception of Execution Strategies"() {

        given:

        def dlRegistry = new DataLoaderRegistry().register("quotes", quotesLoader)

        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(dlRegistry)

        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()

        when:

        def asyncResult = graphql.executeAsync(ExecutionInput.newExecutionInput().query(query))

        //Wait here with timeout, in negative scenario - test will never ends.
        def er = asyncResult.get(6, TimeUnit.SECONDS)

        then:
        //allQuotes should be null, cause it can't be fetched and should present 1 TypeMismatch error
        (er.data == [allQuotes: null]) && er.errors.size() == 1
    }

}
