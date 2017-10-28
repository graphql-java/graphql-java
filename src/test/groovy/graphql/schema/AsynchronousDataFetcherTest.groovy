package graphql.schema

import graphql.GraphQL
import graphql.StarWarsData
import graphql.TestUtil
import graphql.execution.FieldCollector
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring


class AsynchronousDataFetcherTest extends Specification {

    def "A data fetcher can be made asynchronous with AsynchronousDataFetcher#async"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment)

        when:
        DataFetcher asyncDataFetcher = AsynchronousDataFetcher.async({ env -> "value" })

        then:
        asyncDataFetcher.get(environment) instanceof CompletableFuture
        asyncDataFetcher.get(environment).get() == "value"
    }
}
