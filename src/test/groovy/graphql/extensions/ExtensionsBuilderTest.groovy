package graphql.extensions

import graphql.ExecutionResult
import org.jetbrains.annotations.NotNull
import spock.lang.Specification

import static graphql.extensions.ExtensionsBuilder.newExtensionsBuilder

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
            Map<Object, Object> merge(@NotNull Map<?, Object> leftMap, @NotNull Map<?, Object> rightMap) {
                return rightMap as Map<Object, Object>
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
}
