package graphql.schema

import graphql.AssertException
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.language.Node
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT
import static graphql.introspection.Introspection.DirectiveLocation.UNION
import static graphql.language.AstPrinter.printAst

class GraphQLDirectiveTest extends Specification {

    def "object can be transformed"() {
        given:
        def startingDirective = GraphQLDirective.newDirective()
                .name("D1")
                .description("D1_description")
                .validLocation(ARGUMENT_DEFINITION)
                .validLocations(FIELD_DEFINITION, OBJECT)
                .argument(GraphQLArgument.newArgument().name("argStr").type(GraphQLString))
                .argument(GraphQLArgument.newArgument().name("argInt").type(GraphQLInt))
                .build()
        when:
        def transformedDirective = startingDirective.transform({ builder ->
            builder.name("D2")
                    .description("D2_description")
                    .clearValidLocations()
                    .validLocations(INTERFACE, UNION)
                    .argument(GraphQLArgument.newArgument().name("argInt").type(GraphQLBoolean))
                    .argument(GraphQLArgument.newArgument().name("argIntAdded").type(GraphQLInt))
        })
        then:
        startingDirective.name == "D1"
        startingDirective.description == "D1_description"
        startingDirective.validLocations() == [ARGUMENT_DEFINITION, FIELD_DEFINITION, OBJECT].toSet()
        startingDirective.arguments.size() == 2
        startingDirective.getArgument("argStr").type == GraphQLString
        startingDirective.getArgument("argInt").type == GraphQLInt

        transformedDirective.name == "D2"
        transformedDirective.description == "D2_description"
        transformedDirective.validLocations() == [INTERFACE, UNION].toSet()
        transformedDirective.arguments.size() == 3
        transformedDirective.getArgument("argStr").type == GraphQLString
        transformedDirective.getArgument("argInt").type == GraphQLBoolean // swapped
        transformedDirective.getArgument("argIntAdded").type == GraphQLInt
    }

    def "integration test of directives on elements"() {
        def sdl = """
            directive @d1(arg : String) on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |  ARGUMENT_DEFINITION | INTERFACE | UNION |
                                ENUM | ENUM_VALUE |  INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @dr(arg : String) repeatable on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |  ARGUMENT_DEFINITION | INTERFACE | UNION |
                                ENUM | ENUM_VALUE |  INPUT_OBJECT | INPUT_FIELD_DEFINITION

            
            schema @d1 @dr(arg : "a1") @dr(arg : "a2")   {
                query : Query
            }
            
            type Query @d1 @dr(arg : "a1") @dr(arg : "a2") {
                field(arg : String @d1 @dr(arg : "a1") @dr(arg : "a2") )  : String @d1 @dr(arg : "a1") @dr(arg : "a2")
            }
            
            input Input @d1 @dr(arg : "a1") @dr(arg : "a2") {
                inputField : String @d1 @dr(arg : "a1") @dr(arg : "a2")
            }
            
 
            interface InterfaceType @d1 @dr(arg : "a1") @dr(arg : "a2") {
                interfaceField : String @d1 @dr(arg : "a1") @dr(arg : "a2")
            }
            
            type A { a : String }
            
            type B { b : String } 
            
            union UnionType @d1 @dr(arg : "a1") @dr(arg : "a2") = A | B
            
            scalar ScalarType @d1 @dr(arg : "a1") @dr(arg : "a2")
            
            enum EnumType @d1 @dr(arg : "a1") @dr(arg : "a2") {
                EnumVal @d1 @dr(arg : "a1") @dr(arg : "a2")
            }
        """

        when:
        def schema = TestUtil.schema(sdl)
        then:
        schema.getSchemaAppliedDirective("d1").name == "d1"
        schema.getAllSchemaAppliedDirectivesByName().keySet() == ["d1", "dr"] as Set

        schema.getAllSchemaAppliedDirectivesByName()["d1"].size() == 1
        schema.getAllSchemaAppliedDirectivesByName()["dr"].size() == 2
        schema.getAllSchemaAppliedDirectivesByName()["dr"].collect({ printAst(it.getArgument("arg").argumentValue.value) }) == ['"a1"', '"a2"']

        when:
        def queryType = schema.getObjectType("Query")

        then:
        assertDirectiveContainer(queryType)

        when:
        def fieldDef = queryType.getFieldDefinition("field")

        then:
        assertDirectiveContainer(fieldDef)

        when:
        def arg = fieldDef.getArgument("arg")

        then:
        assertDirectiveContainer(arg)

        when:
        def inputType = schema.getType("Input") as GraphQLInputObjectType

        then:
        assertDirectiveContainer(inputType)

        when:
        def inputField = inputType.getField("inputField")

        then:
        assertDirectiveContainer(inputField)


        when:
        def enumType = schema.getType("EnumType") as GraphQLEnumType

        then:
        assertDirectiveContainer(enumType)

        when:
        def enumVal = enumType.getValue("EnumVal")

        then:
        assertDirectiveContainer(enumVal)

        when:
        def interfaceType = schema.getType("InterfaceType") as GraphQLInterfaceType

        then:
        assertDirectiveContainer(interfaceType)

        when:
        def interfaceField = interfaceType.getFieldDefinition("interfaceField")

        then:
        assertDirectiveContainer(interfaceField)

        when:
        def unionType = schema.getType("UnionType") as GraphQLUnionType

        then:
        assertDirectiveContainer(unionType)


        when:
        def scalarType = schema.getType("ScalarType") as GraphQLScalarType

        then:
        assertDirectiveContainer(scalarType)
    }

    def "throws an error on missing required properties"() {
        given:
        def validDirective = GraphQLDirective.newDirective()
                .name("dir")
                .validLocation(Introspection.DirectiveLocation.SCALAR)
                .build()

        when:
        validDirective.transform { it.name(null) }

        then:
        def e = thrown(AssertException)
        e.message.contains("Name must be non-null, non-empty")

        when:
        validDirective.transform { it.replaceArguments(null) }

        then:
        def e2 = thrown(AssertException)
        e2.message.contains("arguments must not be null")

        when:
        validDirective.transform { it.clearValidLocations() }

        then:
        def e3 = thrown(AssertException)
        e3.message.contains("locations can't be empty")
    }


    static boolean assertDirectiveContainer(GraphQLDirectiveContainer container) {
        assert container.hasDirective("d1") // Retain for test coverage
        assert container.hasAppliedDirective("d1")
        assert container.hasAppliedDirective("dr")
        assert !container.hasAppliedDirective("non existent")
        assert container.getDirectives().collect({ it.name }) == ["d1", "dr", "dr"] // Retain for test coverage
        assert container.getAppliedDirectives().collect({ it.name }) == ["d1", "dr", "dr"]
        assert container.getAppliedDirective("d1").name == "d1"
        assert container.getDirectivesByName().keySet() == ["d1"] as Set // Retain for test coverage, there is no equivalent non-repeatable directive method

        assert container.getAllDirectivesByName().keySet() == ["d1", "dr"] as Set // Retain for test coverage
        assert container.getAllAppliedDirectivesByName().keySet() == ["d1", "dr"] as Set
        assert container.getAllAppliedDirectivesByName()["d1"].size() == 1
        assert container.getAllAppliedDirectivesByName()["dr"].size() == 2

        assert container.getAppliedDirectives("d1").size() == 1
        assert container.getAppliedDirectives("dr").size() == 2
        assert container.getAppliedDirectives("dr").collect({ printAst(it.getArgument("arg").argumentValue.value as Node) }) == ['"a1"', '"a2"']

        return true
    }
}
