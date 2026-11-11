package graphql.schema

import graphql.GraphQL
import graphql.schema.validation.InvalidSchemaException
import graphql.schema.validation.SchemaValidationErrorType
import graphql.schema.validation.SchemaValidator
import spock.lang.Specification

import java.util.stream.IntStream
import java.util.stream.Stream

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class ProgrammaticDefaultValuesTest extends Specification {

    def "validation rejects #element #setter #kind inside #shape without consuming it - fast #fast"() {
        given:
        def singleUseValue = singleUseValue(kind)
        def defaultValue = wrapValue(singleUseValue, shape)
        def builder = schemaBuilderWithDefault(element, setter, defaultValue, fast)

        when:
        builder.build()

        then:
        def exception = thrown(InvalidSchemaException)
        exception.errors*.classification == [SchemaValidationErrorType.InvalidDefaultValue]
        exception.message.contains('default values must not contain an Iterator or a Stream')
        kind == 'iterator' ? singleUseValue.next() == 'ok' : singleUseValue.count() == 1

        where:
        [element, setter, kind, shape, fast] << [
                ['argument', 'input field', 'directive argument'],
                ['defaultValueProgrammatic', 'defaultValue'],
                ['iterator', 'stream', 'primitive stream'],
                ['direct', 'map', 'list', 'array', 'nested containers'],
                [false, true]
        ].combinations()
    }

    def "#element defaults preserve #representation input values across executions"() {
        given:
        def defaultValue = repeatableValues(representation)
        def leaf = newInputObject().name('Leaf')
                .field(newInputObjectField().name('name').type(GraphQLString)).build()
        def argument = newArgument().name('arg').type(list(leaf))
        def queryText = '{ test }'
        if (element == 'input field') {
            def root = newInputObject().name('Root')
                    .field(newInputObjectField().name('items').type(list(leaf))
                            .defaultValueProgrammatic(defaultValue)).build()
            argument.type(root)
            queryText = '{ test(arg: {}) }'
        } else {
            argument.defaultValueProgrammatic(defaultValue)
        }
        def query = newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString).argument(argument)).build()
        DataFetcher<String> fetcher = { environment ->
            def value = environment.getArgument('arg')
            def items = element == 'input field' ? value['items'] : value
            items.collect { it['name'] }.join(',')
        }
        def registry = GraphQLCodeRegistry.newCodeRegistry()
                .dataFetcher(FieldCoordinates.coordinates('Query', 'test'), fetcher).build()
        def graphQL = GraphQL.newGraphQL(newSchema().query(query).codeRegistry(registry).build()).build()

        expect:
        (1..2).every {
            def result = graphQL.execute(queryText)
            result.errors.empty && result.getData() == [test: 'ok']
        }

        where:
        [element, representation] << [
                ['argument', 'input field'],
                ['list', 'set', 'array', 'iterable']
        ].combinations()
    }

    def "null and primitive arrays remain supported defaults"() {
        given:
        def input = newInputObject().name('Input')
                .field(newInputObjectField().name('items').type(list(GraphQLInt))
                        .defaultValueProgrammatic([1, 2] as int[]))
                .field(newInputObjectField().name('name').type(GraphQLString)
                        .defaultValueProgrammatic(null)).build()
        def query = newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString)
                        .argument(newArgument().name('input').type(input))
                        .argument(newArgument().name('items').type(list(GraphQLInt))
                                .defaultValueProgrammatic([1, 2] as int[]))
                        .argument(newArgument().name('name').type(GraphQLString)
                                .defaultValueProgrammatic(null))).build()

        expect:
        newSchema().query(query).build() != null
    }

    def "default setters do not traverse values"() {
        given:
        def value = Mock(Iterable)

        when:
        newArgument().defaultValueProgrammatic(value)
        newInputObjectField().defaultValueProgrammatic(value)
        newArgument().defaultValue(value)
        newInputObjectField().defaultValue(value)

        then:
        0 * value.iterator()
    }

    def "validation catches an iterator added after setting the default"() {
        given:
        def value = [name: 'ok']
        def builder = schemaBuilderWithDefault('argument', 'defaultValueProgrammatic', value, false)
        def iterator = ['later'].iterator()
        value['later'] = iterator

        when:
        builder.build()

        then:
        thrown(InvalidSchemaException)
        iterator.next() == 'later'
    }

    def "unchecked FastBuilder defers rejection until explicit schema validation"() {
        given:
        def value = Stream.of('ok')
        def builder = schemaBuilderWithDefault('argument', 'defaultValueProgrammatic', value, true)
        def schema = builder.withValidation(false).build()

        when:
        def errors = new SchemaValidator().validateSchema(schema)

        then:
        errors*.classification == [SchemaValidationErrorType.InvalidDefaultValue]
        value.count() == 1
    }

    def "input field defaults are rejected before argument coercion expands them"() {
        given:
        def value = Stream.of('ok')
        def input = newInputObject().name('Input')
                .field(newInputObjectField().name('items').type(list(GraphQLString))
                        .defaultValueProgrammatic(value)).build()
        def query = newObject().name('Query')
                .field(newFieldDefinition().name('test').type(GraphQLString)
                        .argument(newArgument().name('arg').type(input).defaultValueProgrammatic([:]))).build()

        when:
        newSchema().query(query).build()

        then:
        def exception = thrown(InvalidSchemaException)
        exception.errors*.classification == [SchemaValidationErrorType.InvalidDefaultValue]
        exception.message.contains("Invalid default value for 'items'")
        value.count() == 1
    }

    def "validation handles shared and recursive containers without overlooking an iterator"() {
        given:
        def shared = [name: 'ok']
        def value = [first: shared, second: shared]
        value['self'] = value
        def iterator = ['ok'].iterator()
        value['iterator'] = iterator
        def builder = schemaBuilderWithDefault('argument', 'defaultValueProgrammatic', value, false)

        when:
        builder.build()

        then:
        thrown(InvalidSchemaException)
        iterator.next() == 'ok'
    }

    private static Object schemaBuilderWithDefault(String element, String setter, Object value, boolean fast) {
        def argument = newArgument().name('arg').type(GraphQLString)
        def types = []
        def directive = newDirective().name('probe').validLocation(FIELD_DEFINITION)
        if (element == 'input field') {
            def field = newInputObjectField().name('value').type(GraphQLString)
            field."$setter"(value)
            def input = newInputObject().name('Input').field(field).build()
            argument.type(input)
            types.add(input)
        } else {
            argument."$setter"(value)
        }
        def queryField = newFieldDefinition().name('test').type(GraphQLString)
        if (element == 'directive argument') {
            directive.argument(argument)
        } else {
            queryField.argument(argument)
        }
        def query = newObject().name('Query').field(queryField).build()
        return fast
                ? new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                        .addTypes(types).additionalDirective(directive.build()).withValidation(true)
                : newSchema().query(query).additionalDirective(directive.build())
    }

    private static Object singleUseValue(String kind) {
        if (kind == 'iterator') {
            return ['ok'].iterator()
        }
        return kind == 'stream' ? Stream.of('ok') : IntStream.of(1)
    }

    private static Object wrapValue(Object value, String shape) {
        switch (shape) {
            case 'map': return [value: value]
            case 'list': return [value]
            case 'array': return [value] as Object[]
            case 'nested containers': return [outer: [[value] as Object[]]]
            default: return value
        }
    }

    private static Object repeatableValues(String representation) {
        def values = [[name: 'ok']]
        switch (representation) {
            case 'array': return values as Object[]
            case 'set': return values as Set
            case 'iterable': return { -> values.iterator() } as Iterable
            default: return values
        }
    }
}
