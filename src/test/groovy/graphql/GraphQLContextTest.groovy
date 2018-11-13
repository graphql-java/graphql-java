package graphql

import graphql.schema.DataFetcher
import spock.lang.Specification

import java.util.stream.Collectors

import static graphql.ExecutionInput.newExecutionInput

class GraphQLContextTest extends Specification {

    def buildContext(Map<String, String> map) {
        def context = GraphQLContext.newContext()
        map.forEach({ k, v -> context.of(k, v) })
        return context.build()
    }

    int sizeOf(GraphQLContext graphQLContext) {
        graphQLContext.stream().count();
    }

    def "of builder"() {
        def context
        when:
        context = GraphQLContext.newContext().of("k1", "v1").build()
        then:
        context.get("k1") == "v1"
        sizeOf(context) == 1

        when:
        context = GraphQLContext.newContext().of(
                "k1", "v1",
                "k2", "v2"
        ).build()
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        sizeOf(context) == 2

        when:
        context = GraphQLContext.newContext().of(
                "k1", "v1",
                "k2", "v2",
                "k3", "v3",
        ).build()
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        context.get("k3") == "v3"
        sizeOf(context) == 3

        when:
        context = GraphQLContext.newContext().of(
                "k1", "v1",
                "k2", "v2",
                "k3", "v3",
                "k4", "v4",
        ).build()
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        context.get("k3") == "v3"
        context.get("k4") == "v4"
        sizeOf(context) == 4

        when:
        context = GraphQLContext.newContext().of(
                "k1", "v1",
                "k2", "v2",
                "k3", "v3",
                "k4", "v4",
                "k5", "v5",
        ).build()
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        context.get("k3") == "v3"
        context.get("k4") == "v4"
        context.get("k5") == "v5"
        sizeOf(context) == 5
    }

    def "put works"() {
        def context
        when:
        context = buildContext([k1: "v1"])
        context.put("k1", "v1delta")
        then:
        context.get("k1") == "v1delta"
        context.hasKey("k1")
        sizeOf(context) == 1
    }

    def "putAll works"() {
        when:
        def context = buildContext([k1: "v1"])
        def context2 = buildContext([k2: "v2"])
        context.putAll(context2)
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"

        sizeOf(context) == 2
    }

    def "hasKey works"() {
        def context
        when:
        context = buildContext([k1: "v1", k2: "k2"])
        then:
        context.hasKey("k1")
        context.hasKey("k2")
        !context.hasKey("k3")
    }

    def "getOrDefault works"() {
        def context
        when:
        context = buildContext([k1: "v1", k2: "k2"])
        then:
        context.getOrDefault("k3", "default") == "default"
    }

    def "getOrEmpty works"() {
        def context
        when:
        context = buildContext([k1: "v1", k2: "k2"])
        then:
        context.getOrEmpty("k1").isPresent()
        context.getOrEmpty("k2").isPresent()
        !context.getOrEmpty("k3").isPresent()
    }

    def "stream works"() {
        def context
        when:
        context = buildContext([k1: "v1", k2: "k2"])
        def keys = context.stream().map({ entry -> entry.key }).collect(Collectors.joining())
        then:
        keys == "k1k2"
    }

    def "delete works"() {
        def context
        when:
        context = buildContext([k1: "v1"])
        context.delete("k1")
        then:
        !context.hasKey("k1")
        context.get("k1") == null
        context.getOrDefault("k1", "default") == "default"

        sizeOf(context) == 0
    }

    def "graphql context integration test"() {
        def spec = '''
            type Query {
                field : String
            }
        '''

        DataFetcher df = { env ->
            GraphQLContext context = env.context
            return context.get("ctx1")
        }
        def graphQL = TestUtil.graphQL(spec, ["Query": ["field": df]]).build()

        def context = GraphQLContext.newContext().of("ctx1", "ctx1value").build()
        ExecutionInput input = newExecutionInput().query("{ field }")
                .context(context).build()

        when:
        def executionResult = graphQL.execute(input)
        then:
        executionResult.errors.isEmpty()
        executionResult.data == [field: "ctx1value"]
    }
}
