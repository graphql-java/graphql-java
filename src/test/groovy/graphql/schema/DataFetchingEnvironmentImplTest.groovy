package graphql.schema

import graphql.execution.ExecutionId
import spock.lang.Specification

import static graphql.execution.ExecutionContextBuilder.*
import static graphql.schema.DataFetchingEnvironmentBuilder.*

class DataFetchingEnvironmentImplTest extends Specification {

    def executionContext = newExecutionContextBuilder().executionId(ExecutionId.from("123")).build()

    def "immutable arguments"() {
        def dataFetchingEnvironment = newDataFetchingEnvironment(executionContext).arguments([arg: "argVal"]).build()

        when:
        def value = dataFetchingEnvironment.getArguments().get("arg")
        then:
        value == "argVal"
        when:
        dataFetchingEnvironment.getArguments().put("arg", "some other value")
        value = dataFetchingEnvironment.getArguments().get("arg")
        then:
        value == "argVal"
    }
}
