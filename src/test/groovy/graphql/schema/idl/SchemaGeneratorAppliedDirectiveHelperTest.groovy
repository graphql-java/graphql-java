package graphql.schema.idl


import graphql.TestUtil
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLInputType
import graphql.schema.GraphqlTypeComparatorRegistry
import spock.lang.Specification

class SchemaGeneratorAppliedDirectiveHelperTest extends Specification {

    def "directive construction preserves supplied nulls defaults order and AST capture"() {
        given:
        def source = '''
            directive @tag(zed: Int = 7, text: String = "fallback", obj: Payload = {value: 1}, absent: String) repeatable on FIELD_DEFINITION
            directive @empty on FIELD_DEFINITION
            input Payload { value: Int }
            type Query { first: String @tag(text: null, zed: 2) @tag @empty }
            extend type Query { second: String @tag(obj: {value: 4}) }
        '''
        def options = SchemaGenerator.Options.defaultOptions()
                .captureAstDefinitions(captureAst)
                .useAppliedDirectivesOnly(appliedOnly)
        def wiring = RuntimeWiring.newRuntimeWiring()
                .comparatorRegistry(sortByName ? GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY : GraphqlTypeComparatorRegistry.AS_IS_REGISTRY)
                .build()

        when:
        def schema = generator.makeExecutableSchema(options, new SchemaParser().parse(source), wiring)
        def field = schema.queryType.getFieldDefinition("first")
        def tags = field.getAppliedDirectives("tag")
        def supplied = tags[0]
        def omitted = tags[1]

        then:
        tags.size() == 2
        supplied.arguments*.name == (sortByName ? ['absent', 'obj', 'text', 'zed'] : ['text', 'zed', 'obj', 'absent'])
        supplied.getArgument("text").hasSetValue()
        supplied.getArgument("text").value == null
        supplied.getArgument("zed").value == 2
        supplied.getArgument("obj").value == [value: 1]
        !supplied.getArgument("absent").hasSetValue()
        (supplied.definition != null) == captureAst
        (supplied.getArgument("text").definition != null) == captureAst
        supplied.getArgument("obj").definition == null
        omitted.getArgument("zed").value == 7
        omitted.getArgument("text").value == "fallback"
        omitted.getArgument("obj").value == [value: 1]
        schema.queryType.getFieldDefinition("second").getAppliedDirective("tag").getArgument("obj").value == [value: 4]
        field.getAppliedDirective("empty").arguments.empty

        if (appliedOnly) {
            assert field.directives.empty
        } else {
            def legacy = field.getDirectives("tag")[0]
            assert legacy.arguments*.name == supplied.arguments*.name
            assert legacy.repeatable
            assert GraphQLArgument.getArgumentValue(legacy.getArgument("text")) == null
            assert legacy.getArgument("text").hasSetValue()
            assert GraphQLArgument.getArgumentDefaultValue(legacy.getArgument("text")) == "fallback"
            assert GraphQLArgument.getArgumentValue(legacy.getArgument("obj")) == [value: 1]
            assert !legacy.getArgument("absent").hasSetValue()
            assert (legacy.getArgument("obj").definition != null) == captureAst
            assert field.getDirective("empty").arguments.empty
        }

        where:
        [generator, captureAst, appliedOnly, sortByName] << [
                [new SchemaGenerator(), new FastSchemaGenerator()], [true, false], [true, false], [true, false]
        ].combinations()
    }

    def "forward directive definitions and separate schema builds keep their own defaults"() {
        given:
        def source = '''
            directive @tag @meta on FIELD_DEFINITION
            directive @meta(value: String = "%s") on DIRECTIVE_DEFINITION
            type Query { field: String @tag }
        '''

        when:
        def first = generator.makeExecutableSchema(new SchemaParser().parse(source.formatted("first")), RuntimeWiring.MOCKED_WIRING)
        def second = generator.makeExecutableSchema(new SchemaParser().parse(source.formatted("second")), RuntimeWiring.MOCKED_WIRING)

        then:
        first.getDirective("tag").getAppliedDirective("meta").getArgument("value").value == "first"
        second.getDirective("tag").getAppliedDirective("meta").getArgument("value").value == "second"
        first.getDirective("tag").getAppliedDirective("meta").getArgument("value").value == "first"

        where:
        generator << [new SchemaGenerator(), new FastSchemaGenerator()]
    }

    def sdl = """
            directive @foo(arg1 : String! = "fooArg1Value", arg2 : String) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @bar(arg1 : String, arg2 : String) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            directive @complex(complexArg1 : ComplexInput! = { name : "Boris", address : { number : 10 street : "Downing St", town : "London" }}) on 
                SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                INTERFACE | UNION |  ENUM | ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                field : Bar @foo(arg2 : "arg2Value") @bar(arg1 : "barArg1Value" arg2 : "arg2Value")
                complexField : Bar @complex
            }
            
            type Bar @foo(arg1 : "BarTypeValue" arg2 : "arg2Value") {
                bar : String
            }
            
            input ComplexInput {
                name : String
                address : Address
            }
            
            input Address {
                number : Int
                street : String
                town : String
            }
                
        """

    def "can capture applied directives and legacy directives"() {

        when:
        def schema = TestUtil.schema(sdl)
        def field = schema.getObjectType("Query").getField("field")
        def complexField = schema.getObjectType("Query").getField("complexField")
        def barType = schema.getObjectType("Bar")

        then:

        schema.getDirectives().collect {it.name}.sort() == [
                "bar",
                "complex",
                "defer",
                "deprecated",
                "experimental_disableErrorPropagation",
                "foo",
                "include",
                "oneOf",
                "skip",
                "specifiedBy",
        ]

        field.directives.collect { it.name }.sort() == ["bar", "foo"]
        field.appliedDirectives.collect { it.name }.sort() == ["bar", "foo"]

        barType.directives.collect { it.name }.sort() == ["foo"]
        barType.appliedDirectives.collect { it.name }.sort() == ["foo"]

        def fooAppliedDirective = field.getAppliedDirective("foo")
        fooAppliedDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirective.arguments.collect { it.getValue() }.sort() == ["arg2Value", "fooArg1Value"]

        def fooAppliedDirectiveOnType = barType.getAppliedDirective("foo")
        fooAppliedDirectiveOnType.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirectiveOnType.arguments.collect { it.getValue() }.sort() == ["BarTypeValue", "arg2Value",]

        def complexAppliedDirective = complexField.getAppliedDirective("complex")
        GraphQLInputType complexInputType = schema.getTypeAs("ComplexInput")
        complexAppliedDirective.arguments.collect { it.name }.sort() == ["complexArg1"]
        complexAppliedDirective.arguments.collect { it.getValue() }.sort() == [
                [name:"Boris", address:[number:10, street:"Downing St", town:"London"]]
        ]
    }

    def "can capture ONLY applied directives"() {

        when:
        def options = SchemaGenerator.Options.defaultOptions()
        then:
        !options.isUseAppliedDirectivesOnly() // default is capture both


        when:
        options = SchemaGenerator.Options.defaultOptions().useAppliedDirectivesOnly(true)

        def schema = TestUtil.schema(options, sdl, RuntimeWiring.MOCKED_WIRING)
        def field = schema.getObjectType("Query").getField("field")
        def barType = schema.getObjectType("Bar")

        then:

        schema.getDirectives().collect {it.name}.sort() == [
                "bar",
                "complex",
                "defer",
                "deprecated",
                "experimental_disableErrorPropagation",
                "foo",
                "include",
                "oneOf",
                "skip",
                "specifiedBy",
        ]

        field.directives.collect { it.name }.sort() == []
        field.appliedDirectives.collect { it.name }.sort() == ["bar", "foo"]

        barType.directives.collect { it.name }.sort() == []
        barType.appliedDirectives.collect { it.name }.sort() == ["foo"]


        def fooAppliedDirective = field.getAppliedDirective("foo")
        fooAppliedDirective.arguments.collect { it.name }.sort() == ["arg1", "arg2"]
        fooAppliedDirective.arguments.collect { it.value }.sort() == ["arg2Value", "fooArg1Value"]
    }

}
