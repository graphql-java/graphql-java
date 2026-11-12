package graphql.execution

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.parser.Parser
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import graphql.schema.scalars.ObjectScalar
import graphql.schema.validation.InvalidSchemaException
import graphql.schema.validation.SchemaValidationErrorType
import spock.lang.Specification

import java.util.stream.StreamSupport

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

class InputValueDefaultsTest extends Specification {
    private final InputValueLimits originalLimits = InputValueLimits.getDefaultLimits()

    def cleanup() {
        InputValueLimits.setDefaultLimits(originalLimits)
    }

    private static void limit(int maxDepth) {
        InputValueLimits.setDefaultLimits(InputValueLimits.DEFAULT.transform { it.maxDepth(maxDepth) })
    }

    private static Map nestedMap(int depth) {
        Map value = [:]
        (depth - 1).times { value = [next: value] }
        value
    }

    private static ObjectValue nestedLiteral(int depth) {
        def value = ObjectValue.newObjectValue().build()
        (depth - 1).times {
            value = ObjectValue.newObjectValue().objectField(
                    ObjectField.newObjectField().name('next').value(value).build()).build()
        }
        value
    }

    private static def withDefault(def builder, String representation, Object value) {
        if (representation == 'literal') {
            return builder.defaultValueLiteral(value)
        }
        if (representation == 'external') {
            return builder.defaultValueProgrammatic(value)
        }
        builder.defaultValue(value)
    }

    private static GraphQLSchema schemaWithDefault(String representation, String location, Object value) {
        def recursive = GraphQLInputObjectType.newInputObject().name('Recursive')
                .field(GraphQLInputObjectField.newInputObjectField().name('next')
                        .type(GraphQLTypeReference.typeRef('Recursive'))).build()
        def argument = GraphQLArgument.newArgument().name('arg').type(recursive)
        if (location == 'argument') {
            withDefault(argument, representation, value)
        }
        if (location == 'input field') {
            def field = withDefault(GraphQLInputObjectField.newInputObjectField().name('value').type(recursive), representation, value)
            argument.type(GraphQLInputObjectType.newInputObject().name('Holder').field(field).build())
        }
        def schema = schemaBuilder(argument.build())
        if (location == 'directive') {
            def defaultArgument = withDefault(GraphQLArgument.newArgument().name('arg').type(recursive), representation, value)
            schema.additionalDirective(GraphQLDirective.newDirective().name('example')
                    .validLocation(Introspection.DirectiveLocation.FIELD).argument(defaultArgument).build())
        }
        schema.build()
    }

    private static GraphQLSchema.Builder schemaBuilder(GraphQLArgument argument) {
        def field = GraphQLFieldDefinition.newFieldDefinition().name('echo').type(GraphQLBoolean).argument(argument).build()
        def query = GraphQLObjectType.newObject().name('Query').field(field).build()
        DataFetcher echo = { env -> env.getArgument('arg') != null }
        def registry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates('Query', 'echo'), echo).build()
        GraphQLSchema.newSchema().query(query).codeRegistry(registry)
    }

    def 'schema validation bounds #representation defaults on #location at the exact boundary'() {
        given:
        limit(2)
        def acceptedValue = representation == 'literal' ? nestedLiteral(2) : nestedMap(2)
        def rejectedValue = representation == 'literal' ? nestedLiteral(3) : nestedMap(3)

        expect:
        schemaWithDefault(representation, location, acceptedValue) != null

        when:
        schemaWithDefault(representation, location, rejectedValue)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.errors*.classification == [SchemaValidationErrorType.InvalidDefaultValue]
        exception.message.contains('maximum depth of 2')

        where:
        [representation, location] << [['literal', 'external', 'internal'], ['argument', 'input field', 'directive']].combinations()
    }

    def 'SDL defaults follow the global limit when the schema is built'() {
        given:
        limit(2)
        def prefix = 'input Recursive { next: Recursive } type Query { echo(arg: Recursive = '

        expect:
        TestUtil.schema(prefix + '{next: {}}): Boolean }') != null

        when:
        TestUtil.schema(prefix + '{next: {next: {}}}): Boolean }')

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('maximum depth of 2')
    }

    def 'validated #representation defaults survive stricter request and later global limits'() {
        given:
        limit(3)
        def value = representation == 'literal' ? nestedLiteral(3) : nestedMap(3)
        def schema = schemaWithDefault(representation, 'argument', value)
        def graphQL = GraphQL.newGraphQL(schema).build()
        def requestLimit = InputValueLimits.DEFAULT.transform { it.maxDepth(1) }
        limit(1)

        when:
        def result = graphQL.execute(ExecutionInput.newExecutionInput('{ echo }')
                .graphQLContext { it.put(InputValueLimits.KEY, requestLimit) }.build())
        def introspection = graphQL.execute(ExecutionInput.newExecutionInput(
                '{ __type(name: "Query") { fields { args { defaultValue } } } }')
                .graphQLContext { it.put(InputValueLimits.KEY, requestLimit) }.build())

        then:
        result.errors.empty
        result.data == [echo: true]
        introspection.errors.empty
        introspection.data.__type.fields[0].args[0].defaultValue == '{next : {next : {}}}'
        GraphQLArgument.getArgumentDefaultValue(schema.queryType.getFieldDefinition('echo').getArgument('arg')) == nestedMap(3)

        when:
        def supplied = graphQL.execute(ExecutionInput.newExecutionInput('query($v: Recursive) { echo(arg: $v) }')
                .variables([v: nestedMap(3)])
                .graphQLContext { it.put(InputValueLimits.KEY, requestLimit) }.build())

        then:
        supplied.errors.size() == 1
        supplied.errors[0].message.contains('maximum depth of 1')
        !supplied.dataPresent

        where:
        representation << ['literal', 'external', 'internal']
    }

    def 'omitted #representation input field defaults do not consume the supplied variable depth'() {
        given:
        limit(3)
        def value = representation == 'literal' ? nestedLiteral(3) : nestedMap(3)
        def graphQL = GraphQL.newGraphQL(schemaWithDefault(representation, 'input field', value)).build()
        def input = ExecutionInput.newExecutionInput('query($v: Holder) { echo(arg: $v) }')
                .variables([v: [:]])
                .graphQLContext { it.put(InputValueLimits.KEY, InputValueLimits.DEFAULT.transform { limits -> limits.maxDepth(1) }) }.build()

        expect:
        graphQL.execute(input).errors.empty

        where:
        representation << ['literal', 'external', 'internal']
    }

    def 'deep #representation defaults are rejected before recursive validators or error formatting overflow'() {
        given:
        def value = representation == 'literal' ? nestedLiteral(5000) : nestedMap(5000)

        when:
        schemaWithDefault(representation, 'input field', value)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('maximum depth of 128')
        exception.message.length() < 250

        where:
        representation << ['literal', 'external']
    }

    def 'list defaults count singleton coercion and ignore nonnull wrappers and siblings'() {
        given:
        limit(2)
        def argument = GraphQLArgument.newArgument().name('arg').type(nonNull(list(nonNull(list(GraphQLInt)))))
                .defaultValueProgrammatic([[1, 2], [3, 4]])

        expect:
        schemaBuilder(argument.build()).build() != null

        when:
        limit(1)
        schemaBuilder(argument.defaultValueProgrammatic([1]).build()).build()

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('maximum depth of 1')
    }

    def 'cyclic programmatic defaults fail schema validation without printing the cycle'() {
        given:
        Map value = [:]
        value.next = value

        when:
        schemaWithDefault('external', 'input field', value)

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('maximum depth of 128')
        exception.message.length() < 250
    }

    def 'literal lists and null defaults use the same depth rules'() {
        given:
        limit(2)
        def argument = GraphQLArgument.newArgument().name('arg').type(list(list(GraphQLInt)))
                .defaultValueLiteral(Parser.parseValue('[[1, 2], null, [3]]'))

        expect:
        schemaBuilder(argument.build()).build() != null

        when:
        limit(1)
        schemaBuilder(argument.build()).build()

        then:
        thrown(InvalidSchemaException)

        expect:
        schemaBuilder(argument.defaultValueLiteral(Parser.parseValue('null')).build()).build() != null
        schemaBuilder(argument.defaultValueProgrammatic(null).build()).build() != null
    }

    def 'custom scalar default payloads remain opaque to the depth limit'() {
        given:
        limit(1)
        def argument = GraphQLArgument.newArgument().name('arg').type(ObjectScalar.OBJECT_SCALAR)
                .defaultValueProgrammatic(nestedMap(5000)).build()

        expect:
        schemaBuilder(argument).build() != null
    }

    def 'array defaults follow the same rules as collections'() {
        given:
        limit(2)
        def value = [[1, 2] as int[], [3] as int[]] as Object[]
        def argument = GraphQLArgument.newArgument().name('arg').type(list(list(GraphQLInt)))
                .defaultValueProgrammatic(value).build()

        expect:
        schemaBuilder(argument).build() != null
        GraphQLArgument.getArgumentDefaultValue(argument) == [[1, 2], [3]]
    }

    def 'list defaults using #kind are rejected at validation without consuming them'() {
        given:
        def iterator = Mock(Iterator)
        def values = [iterator: iterator, iterable: { iterator } as Iterable,
                      stream: StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)]
        def argument = GraphQLArgument.newArgument().name('arg').type(list(GraphQLInt))
                .defaultValueProgrammatic(values[kind]).build()

        when:
        schemaBuilder(argument).build()

        then:
        def exception = thrown(InvalidSchemaException)
        exception.message.contains('List defaults must use collections or arrays')
        0 * iterator._

        where:
        kind << ['iterator', 'iterable', 'stream']
    }
}
