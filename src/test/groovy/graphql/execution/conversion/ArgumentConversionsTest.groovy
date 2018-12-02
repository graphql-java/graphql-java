package graphql.execution.conversion

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification

class ArgumentConversionsTest extends Specification {

    static GraphQLInputObjectType inputType(String typeName) {
        GraphQLInputObjectType.newInputObject().name(typeName).build()
    }

    static GraphQLArgument arg(String typeName) {
        def inputObjectType = inputType(typeName)
        GraphQLArgument.newArgument().name("arg").type(inputObjectType).build()
    }

    static ArgumentConverterEnvironmentImpl env(String typeName) {
        def argument = arg(typeName)
        ArgumentConverterEnvironmentImpl.newEnvironment().argument(argument).sourceObject("SourceX").build()
    }

    def "can compose a series of conversions"() {


        def conversions = ArgumentConversions.newConversions()
                .converter("typeA", { env -> "convertedValueA" })
                .converters([
                typeB: { env -> "convertedValueB" } as ArgumentConverter,
                typeC: { env -> "convertedValueC" } as ArgumentConverter])
                .converter(inputType("typeD"), { env -> "convertedValueD" })
                .build()
        when:
        def actual = conversions.convertArgument(env(actualTypeName))

        then:
        actual == expected

        where:
        actualTypeName || expected
        "typeA"        || "convertedValueA"
        "typeB"        || "convertedValueB"
        "typeC"        || "convertedValueC"
        "typeD"        || "convertedValueD"
        "unknownType"  || "SourceX"
    }
}
