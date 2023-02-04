package graphql.extensions

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLTypeUtil
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

import static graphql.extensions.ExtensionsBuilder.newExtensionsBuilder
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class ExtensionsBuilderTest extends Specification {


    def "can merge changes with default behavior"() {
        when:
        def extensions = newExtensionsBuilder().addValue("x", "24")
                .addValues([y: "25", z: "26"])
                .addValues([x: "overwrite"])
                .buildExtensions()
        then:
        extensions == [x: "overwrite", y: "25", z: "26"]

        when:
        extensions = newExtensionsBuilder().addValue("x", "24")
                .addValues([y: "25", z: "26"])
                .addValues([x: "overwrite"])
                .addValues([x: "overwrite2"])
                .addValues([x: "overwrite2"])
                .addValue("x", "overwrite3")
                .addValue("z", "overwriteZ")
                .addValues([a: "1"])
                .buildExtensions()
        then:
        extensions == [x: "overwrite3", y: "25", z: "overwriteZ", a: "1"]
    }

    def "can handle no changes"() {
        when:
        def extensions = newExtensionsBuilder()
                .buildExtensions()
        then:
        extensions == [:]
    }

    def "can handle one changes"() {
        when:
        def extensions = newExtensionsBuilder()
                .addValues([x: "24", y: "25"])
                .buildExtensions()
        then:
        extensions == [x: "24", y: "25"]
    }

    def "can set extensions into an ER"() {


        when:
        def er = ExecutionResult.newExecutionResult().data(["x": "data"]).build()
        def newER = newExtensionsBuilder().addValue("x", "24")
                .addValues([y: "25", z: "26"])
                .addValues([x: "overwrite"])
                .setExtensions(er)
        then:
        newER.data == ["x": "data"]
        newER.extensions == [x: "overwrite", y: "25", z: "26"]

        when:
        er = ExecutionResult.newExecutionResult().data(["x": "data"]).extensions([a: "1"]).build()
        newER = newExtensionsBuilder().addValue("x", "24")
                .addValues([y: "25", z: "26"])
                .addValues([x: "overwrite"])
                .setExtensions(er)
        then:
        newER.data == ["x": "data"]
        newER.extensions == [x: "overwrite", y: "25", z: "26"] // it overwrites - its a set!

    }

    def "can use a custom merger"() {
        ExtensionsMerger merger = new ExtensionsMerger() {
            @Override
            @NotNull
            Map<Object, Object> merge(@NotNull Map<Object, Object> leftMap, @NotNull Map<Object, Object> rightMap) {
                return rightMap
            }
        }
        when:
        def extensions = newExtensionsBuilder(merger)
                .addValue("x", "24")
                .addValues([y: "25", z: "26"])
                .addValues([x: "overwrite"])
                .addValues([the: "end"]).buildExtensions()
        then:
        extensions == [the: "end"]
    }

    DataFetcher extensionDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment env) throws Exception {
            ExtensionsBuilder extensionsBuilder = env.getGraphQlContext().get(ExtensionsBuilder.class)
            def fieldMap = [:]
            fieldMap.put(env.getFieldDefinition().name, GraphQLTypeUtil.simplePrint(env.getFieldDefinition().type))
            extensionsBuilder.addValues([common: fieldMap])
            extensionsBuilder.addValues(fieldMap)
            return "ignored"
        }
    }


    def "integration test that shows it working when they put in a builder"() {
        def sdl = """
        type Query {
            name : String!
            street : String
            id : ID!
        }
        """

        def extensionsBuilder = newExtensionsBuilder()
        extensionsBuilder.addValue("added","explicitly")

        def ei = ExecutionInput.newExecutionInput("query q { name street id }")
                .graphQLContext({ ctx ->
                    ctx.put(ExtensionsBuilder.class, extensionsBuilder) })
                .build()


        def graphQL = TestUtil.graphQL(sdl, newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetchers([
                        name  : extensionDF,
                        street: extensionDF,
                        id    : extensionDF,
                ])))
                .build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.errors.isEmpty()
        er.extensions == [
                "added": "explicitly",
                common: [
                        name  : "String!",
                        street: "String",
                        id    : "ID!",
                ],
                // we break them out so we have common and not common entries
                name  : "String!",
                street: "String",
                id    : "ID!",
        ]
    }

    def "integration test that shows it working when they DONT put in a builder"() {
        def sdl = """
        type Query {
            name : String!
            street : String
            id : ID!
        }
        """

        def ei = ExecutionInput.newExecutionInput("query q { name street id }")
                .build()


        def graphQL = TestUtil.graphQL(sdl, newRuntimeWiring()
                .type(newTypeWiring("Query").dataFetchers([
                        name  : extensionDF,
                        street: extensionDF,
                        id    : extensionDF,
                ])))
                .build()

        when:
        def er = graphQL.execute(ei)
        then:
        er.errors.isEmpty()
        er.extensions == [
                common: [
                        name  : "String!",
                        street: "String",
                        id    : "ID!",
                ],
                // we break them out so we have common and not common entries
                name  : "String!",
                street: "String",
                id    : "ID!",
        ]
    }
}
