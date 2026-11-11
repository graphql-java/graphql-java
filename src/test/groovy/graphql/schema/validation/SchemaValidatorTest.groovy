package graphql.schema.validation

import graphql.Directives
import graphql.TestUtil
import graphql.collect.ImmutableKit
import graphql.execution.InputValueLimits
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.mkDirective
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class SchemaValidatorTest extends Specification {

    static Set<String> sdlErrors(String sdl) {
        try {
            TestUtil.schema(sdl)
            return [] as Set
        } catch (InvalidSchemaException e) {
            return e.errors.collect { it.description } as Set
        }
    }

    static Set<String> errors(Closure<GraphQLSchema> buildSchema) {
        try {
            buildSchema()
            return [] as Set
        } catch (InvalidSchemaException e) {
            return e.errors.collect { it.description } as Set
        }
    }

    static String loadResource(String name) {
        SchemaValidatorTest.class.getClassLoader().getResource(name).getText("UTF-8")
    }

    def "large schemas are valid"() {
        given:
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(loadResource(schemaFile)),
                RuntimeWiring.MOCKED_WIRING)

        expect:
        new SchemaValidator().validateSchema(schema).isEmpty()

        where:
        schemaFile << [
                "large-schema-1.graphqls",
                "large-schema-2.graphqls",
                "large-schema-3.graphqls",
                "large-schema-4.graphqls",
                "large-schema-federated-1.graphqls",
                "extra-large-schema-1.graphqls",
        ]
    }

    def "reports errors from every rule in a single validation"() {
        when:
        TestUtil.schema('''
            type Query {
                a(cycle: Cycle, old: Int! @deprecated, filter: Filter): String
                b(defaults: DefaultCycleA): String
                c(oneOf: OneOfA, other: OneOfC): String
            }

            input Cycle { first: Cycle! }
            input Filter { required: Int! @deprecated }
            input DefaultCycleA { b: DefaultCycleB = {} }
            input DefaultCycleB { a: DefaultCycleA = {} }
            input OneOfA @oneOf { b: OneOfB }
            input OneOfB @oneOf { a: OneOfA }
            input OneOfC @oneOf { x: Int! y: Int = 1 }
        ''')

        then:
        def e = thrown(InvalidSchemaException)
        e.errors*.classification as Set == [
                SchemaValidationErrorType.UnbrokenInputCycle,
                SchemaValidationErrorType.RequiredFieldArgumentCannotBeDeprecated,
                SchemaValidationErrorType.RequiredInputFieldCannotBeDeprecated,
                SchemaValidationErrorType.DefaultValueCircularRef,
                SchemaValidationErrorType.OneOfNotInhabited,
                SchemaValidationErrorType.OneOfNonNullableField,
                SchemaValidationErrorType.OneOfDefaultValueOnField,
        ] as Set
    }

    def "elements shared between parents are validated under each of them"() {
        given:
        GraphQLInputObjectField sharedField = newInputObjectField().name("shared").type(nonNull(GraphQLString)).build()
        def plainInput = newInputObject().name("Plain").field(sharedField).build()
        def oneOfInput = newInputObject().name("Choice").field(sharedField)
                .withAppliedDirective(Directives.OneOfDirective.toAppliedDirective())
                .build()
        def sharedArgument = newArgument().name("required").type(nonNull(GraphQLInt)).deprecate("old").build()
        def query = newObject().name("Query")
                .field(newFieldDefinition().name("plain").type(GraphQLString).argument(newArgument().name("arg").type(plainInput)))
                .field(newFieldDefinition().name("choice").type(GraphQLString).argument(newArgument().name("arg").type(oneOfInput)))
                .field(newFieldDefinition().name("first").type(GraphQLString).argument(sharedArgument))
                .field(newFieldDefinition().name("second").type(GraphQLString).argument(sharedArgument))
                .build()

        expect:
        errors { newSchema().query(query).build() } == [
                "OneOf input field Choice.shared must be nullable.",
                "Required argument 'required' on field 'first' cannot be deprecated.",
                "Required argument 'required' on field 'second' cannot be deprecated.",
        ] as Set
    }

    def "FastBuilder validates schemas when validation is enabled"() {
        given:
        def query = newObject().name("Query")
                .field(newFieldDefinition().name("__bad").type(GraphQLString))
                .build()

        expect:
        errors {
            new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                    .withValidation(true)
                    .build()
        } == ['"__bad" in "Query" must not begin with "__", which is reserved by GraphQL introspection.'] as Set
    }

    def "default values nested beyond the input depth limit stop validation early"() {
        given:
        Map<String, Object> tooDeep = [:]
        (InputValueLimits.DEFAULT_MAX_DEPTH + 1).times { tooDeep = [next: tooDeep] }
        def recursive = newInputObject().name("Recursive")
                .field(newInputObjectField().name("next").type(GraphQLTypeReference.typeRef("Recursive")))
                .build()
        def query = newObject().name("Query")
                .field(newFieldDefinition().name("echo").type(GraphQLString)
                        .argument(newArgument().name("arg").type(recursive).defaultValueProgrammatic(tooDeep)))
                .field(newFieldDefinition().name("__bad").type(GraphQLString))
                .build()

        when:
        def errors = errors { newSchema().query(query).build() }

        then:
        errors.size() == 1
        errors.every { it.contains("exceeds the maximum depth of " + InputValueLimits.DEFAULT_MAX_DEPTH) }
    }

    def "named types other than the schema type kinds are skipped"() {
        given:
        def query = newObject().name("Query").field(newFieldDefinition().name("hello").type(GraphQLString)).build()
        def schema = new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                .addType(GraphQLTypeReference.typeRef("Dangling"))
                .build()

        expect:
        schema.getType("Dangling") instanceof GraphQLTypeReference
        new SchemaValidator().validateSchema(schema).isEmpty()
    }

    def "applied directives without a definition are rejected"() {
        given:
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("hello").type(GraphQLString).withDirective(mkDirective("unknown", FIELD_DEFINITION)))
                .build()

        expect:
        errors { newSchema().query(query).build() } == ["A definition for directive 'unknown' could not be found"] as Set
    }

    def "applied directive arguments are validated for programmatic schemas"() {
        given:
        def arg = newArgument().name("arg").type(nonNull(GraphQLInt)).build()
        def directive = mkDirective("cached", ARGUMENT_DEFINITION, arg)
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("hello").type(GraphQLString).withAppliedDirective(directive.toAppliedDirective()))
                .build()

        expect:
        errors { newSchema().query(query).additionalDirective(directive).build() } == ["Invalid argument 'arg' for applied directive of name 'cached'"] as Set
    }

    def "legacy applied directive arguments are validated for programmatic schemas"() {
        given:
        def arg = newArgument().name("arg").type(GraphQLInt).valueProgrammatic(ImmutableKit.emptyMap()).build()
        def directive = mkDirective("cached", ARGUMENT_DEFINITION, arg)
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition().name("hello").type(GraphQLString).argument(arg).withDirective(directive))
                .build()

        expect:
        errors { newSchema().query(query).additionalDirective(directive).build() }
                .contains("Invalid argument 'arg' for applied directive of name 'cached'")
    }

    def "valid applied directive arguments are accepted"() {
        expect:
        sdlErrors('''
            directive @cached(key: String, ttl: Int! = 10) on FIELD_DEFINITION
            type Query { hello: String @cached(key: "k") other: String @cached(key: "k", ttl: 5) }
        ''').isEmpty()
    }

    def "invalid default values are rejected: #scenario"() {
        expect:
        sdlErrors(sdl).size() == 1
        sdlErrors(sdl).every { it.startsWith("Invalid default value") }

        where:
        scenario                                    | sdl
        "argument default missing required field"   | 'type Query { foo(arg: Input = {}): String } input Input { required: String! }'
        "argument default with null required field" | 'type Query { foo(arg: Input = {required: null}): String } input Input { required: String! }'
        "input field default of the wrong type"     | 'type Query { foo(arg: Input): String } input Input { count: Int = "many" }'
    }

    def "valid default values are accepted"() {
        expect:
        sdlErrors('''
            type Query { foo(arg: Input = {required: "x"}, limit: Int = 10, tags: [String] = ["a"]): String }
            input Input { required: String! optional: Int = 1 }
        ''').isEmpty()
    }

    def "a validator instance can validate several schemas"() {
        given:
        def validator = new SchemaValidator()
        def schema = new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse('type Query { a(arg: A): String } input A { b: A = {} }'),
                RuntimeWiring.MOCKED_WIRING)

        expect:
        validator.validateSchema(schema) == validator.validateSchema(schema)
        validator.validateSchema(schema).size() == 1
    }
}
