package graphql.schema

import graphql.AssertException
import graphql.GraphQL
import spock.lang.Specification

import java.util.stream.IntStream
import java.util.stream.Stream

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class ProgrammaticDefaultValuesTest extends Specification {

    def "#element #setter rejects #kind inside #shape without consuming it"() {
        given:
        def builder = element == 'argument' ? newArgument() : newInputObjectField()
        def singleUseValue = singleUseValue(kind)
        def defaultValue = wrapValue(singleUseValue, shape)

        when:
        builder."$setter"(defaultValue)

        then:
        def exception = thrown(AssertException)
        exception.message == 'Default values must not contain an Iterator or a Stream'
        kind == 'iterator' ? singleUseValue.next() == 'ok' : singleUseValue.count() == 1

        where:
        [element, setter, kind, shape] << [
                ['argument', 'input field'],
                ['defaultValueProgrammatic', 'defaultValue'],
                ['iterator', 'stream', 'primitive stream'],
                ['direct', 'map', 'list', 'array', 'nested containers']
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
        expect:
        newArgument().defaultValueProgrammatic(null) != null
        newInputObjectField().defaultValueProgrammatic(null) != null
        newArgument().defaultValueProgrammatic([1, 2] as int[]) != null
        newInputObjectField().defaultValueProgrammatic([1, 2] as int[]) != null
    }

    def "checking defaults handles shared and recursive Java containers by identity"() {
        given:
        def shared = [name: 'ok']
        def value = [first: shared, second: shared]
        value['self'] = value

        expect:
        newArgument().defaultValueProgrammatic(value) != null
        newInputObjectField().defaultValueProgrammatic(value) != null
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
