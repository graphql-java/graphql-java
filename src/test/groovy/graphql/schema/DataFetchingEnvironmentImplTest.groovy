package graphql.schema

import graphql.Scalars
import graphql.execution.ExecutionId
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective

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

    def "directives are present from map"() {
        GraphQLDirective d1 = newDirective().name("d1").argument(newArgument().name("arg1").type(GraphQLString).value("v1")).build()
        GraphQLDirective d2 = newDirective().name("d2").argument(newArgument().name("arg2").type(Scalars.GraphQLInt).value(2)).build()
        def dataFetchingEnvironment = newDataFetchingEnvironment(executionContext).directives(["d1": d1, "d2": d2]).build()

        when:
        def directives = dataFetchingEnvironment.getDirectives()
        def actualD1 = dataFetchingEnvironment.getDirective("d1")
        def arg1 = dataFetchingEnvironment.getDirectiveArgument("d1", "arg1")
        def argX = dataFetchingEnvironment.getDirectiveArgument("d1", "argX")

        then:
        directives.keySet() == ["d1", "d2"] as Set

        actualD1 == d1

        arg1.name == "arg1"
        arg1.type.getName() == GraphQLString.getName()
        arg1.value == "v1"

        argX == null

    }
}
