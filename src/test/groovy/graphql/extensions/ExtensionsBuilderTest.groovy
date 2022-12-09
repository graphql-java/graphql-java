package graphql.extensions


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
                .addValue("x", "overwrite3")
                .addValue("z", "overwriteZ")
                .buildExtensions()
        then:
        extensions == [x: "overwrite3", y: "25", z: "overwriteZ"]
    }
}
