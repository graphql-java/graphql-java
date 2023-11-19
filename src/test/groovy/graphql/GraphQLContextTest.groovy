package graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.util.function.Consumer
import java.util.stream.Collectors

import static graphql.ExecutionInput.newExecutionInput

class GraphQLContextTest extends Specification {

    def buildContext(Map<String, String> map) {
        def context = GraphQLContext.newContext()
        map.forEach({ k, v -> context.of(k, v) })
        return context.build()
    }

    int sizeOf(GraphQLContext graphQLContext) {
        graphQLContext.stream().count()
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

        when:
        context = GraphQLContext.newContext()
                .of("k1", "v1")
                .of("k2", "v2")
                .of(["k3": "v3"]).build()
        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        context.get("k3") == "v3"
        sizeOf(context) == 3

        when:
        context = GraphQLContext.of(["k1": "v1", "k2": "v2"])

        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        sizeOf(context) == 2

        when:
        context = GraphQLContext.of({ it.of("k1", "v1") } as Consumer<GraphQLContext.Builder>)

        then:
        context.get("k1") == "v1"
        sizeOf(context) == 1
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

        when:
        context = buildContext([k1: "v1"])
        context.putAll([k2: "v2"])

        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        sizeOf(context) == 2

        when:
        context = buildContext([k1: "v1"])
        context.putAll(GraphQLContext.newContext().of("k2", "v2"))

        then:
        context.get("k1") == "v1"
        context.get("k2") == "v2"
        sizeOf(context) == 2

        when:
        context = buildContext([k1: "v1"])
        context.putAll({ it.of([k2: "v2"]) } as Consumer<GraphQLContext.Builder>)

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

    def "compute works"() {
        def context
        when:
        context = buildContext([k1: "foo"])
        then:
        context.compute("k1", (k, v) -> v ? v + "bar" : "default") == "foobar"
        context.get("k1") == "foobar"
        context.compute("k2", (k, v) -> v ? "new" : "default") == "default"
        context.get("k2") == "default"
        !context.compute("k3", (k, v) -> null)
        !context.hasKey("k3")
        sizeOf(context) == 2
    }

    def "computeIfAbsent works"() {
        def context
        when:
        context = buildContext([k1: "v1", k2: "v2"])
        then:
        context.computeIfAbsent("k1", k -> "default") == "v1"
        context.get("k1") == "v1"
        context.computeIfAbsent("k2", k -> null) == "v2"
        context.get("k2") == "v2"
        context.computeIfAbsent("k3", k -> "default") == "default"
        context.get("k3") == "default"
        !context.computeIfAbsent("k4", k -> null)
        !context.hasKey("k4")
        sizeOf(context) == 3
    }

    def "computeIfPresent works"() {
        def context
        when:
        context = buildContext([k1: "foo", k2: "v2"])
        then:
        context.computeIfPresent("k1", (k, v) -> v + "bar") == "foobar"
        context.get("k1") == "foobar"
        !context.computeIfPresent("k2", (k, v) -> null)
        !context.hasKey("k2")
        !context.computeIfPresent("k3", (k, v) -> v + "bar")
        !context.hasKey("k3")
        !context.computeIfPresent("k4", (k, v) -> null)
        !context.hasKey("k4")
        sizeOf(context) == 1
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

        DataFetcher df = { DataFetchingEnvironment env ->
            GraphQLContext context = env.graphQlContext
            return context.get("ctx1")
        }
        def graphQL = TestUtil.graphQL(spec, ["Query": ["field": df]]).build()

        ExecutionInput input = newExecutionInput().query("{ field }").build()
        input.getGraphQLContext().putAll([ctx1: "ctx1value"])

        when:
        def executionResult = graphQL.execute(input)
        then:
        executionResult.errors.isEmpty()
        executionResult.data == [field: "ctx1value"]
    }
}
