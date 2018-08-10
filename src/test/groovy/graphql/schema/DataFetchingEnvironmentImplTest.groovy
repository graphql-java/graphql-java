package graphql.schema

import graphql.execution.ExecutionId
import spock.lang.Specification

import static graphql.execution.ExecutionContextBuilder.*
import static graphql.schema.DataFetchingEnvironmentBuilder.*

class DataFetchingEnvironmentImplTest extends Specification {

    def executionContext = newExecutionContextBuilder().executionId(ExecutionId.from("123")).build()

    def "immutable arguments"() {
        def dataFetchingEnvironment = newDataFetchingEnvironment(executionContext).arguments([some: "arg"]).build()

        when:
        dataFetchingEnvironment.getArguments().put("mutation", "not possible")
        then:
        thrown(UnsupportedOperationException)
    }
}
