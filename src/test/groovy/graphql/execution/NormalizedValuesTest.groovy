package graphql.execution

import graphql.AssertException
import graphql.GraphQLContext
import graphql.TestUtil
import graphql.language.AstPrinter
import graphql.language.Field
import graphql.language.OperationDefinition
import graphql.language.StringValue
import graphql.parser.Parser
import graphql.schema.InputValueWithState
import graphql.schema.visibility.DefaultGraphqlFieldVisibility
import spock.lang.Specification

class NormalizedValuesTest extends Specification {
    def schema = TestUtil.schema('''
        enum Color { RED BLUE }
        input Input { id: ID ids: [ID!] nested: Input color: Color }
        type Query { f(input: Input ids: [ID!] id: ID! fallback: ID optional: Int nil: ID): String }
    ''')
    def context = GraphQLContext.getDefault()

    def "normalized variables preserve nested type information missing values and explicit nulls"() {
        given:
        def operation = operation('''query($input: Input, $ids: [ID!], $id: ID!, $fallback: ID = "default", $absent: Int, $nil: ID) {
            f(input: $input, ids: $ids, id: $id, fallback: $fallback, optional: $absent, nil: $nil)
        }''')
        def raw = RawVariables.of([input: [id: 7, ids: [1, 2], nested: [id: null], color: 'RED'], ids: 9, id: 123, nil: null])

        when:
        def variables = ValuesResolver.getNormalizedVariableValues(schema, operation.variableDefinitions, raw, context, Locale.ENGLISH).toMap()
        def field = operation.selectionSet.selections[0] as Field
        def arguments = ValuesResolver.getNormalizedArgumentValues(schema.queryType.getFieldDefinition('f').arguments, field.arguments, variables)

        then:
        variables.keySet() == ['input', 'ids', 'id', 'fallback', 'nil'] as Set
        variables.input.typeName == 'Input'
        variables.input.value.id.typeName == 'ID'
        variables.input.value.id.value.value == '7'
        variables.input.value.ids.typeName == '[ID!]'
        variables.input.value.ids.value*.value == ['1', '2']
        variables.input.value.nested.value.id.value == null
        variables.input.value.color.value.name == 'RED'
        variables.ids.value*.value == ['9']
        variables.id.typeName == 'ID!'
        variables.id.value.value == '123'
        variables.fallback.value.value == 'default'
        variables.nil.value == null
        arguments.keySet() == ['input', 'ids', 'id', 'fallback', 'nil'] as Set
        arguments.input.value == variables.input.value
    }

    def "normalized argument literals retain enum and list structure and omit absent nested variables"() {
        given:
        def operation = operation('''query($absent: ID) {
            f(input: {id: $absent, ids: [3, 4], nested: {id: null}, color: BLUE}, ids: 5, id: 6)
        }''')
        def field = operation.selectionSet.selections[0] as Field

        when:
        def arguments = ValuesResolver.getNormalizedArgumentValues(schema.queryType.getFieldDefinition('f').arguments, field.arguments, [:])

        then:
        arguments.input.value.keySet() == ['ids', 'nested', 'color'] as Set
        arguments.input.value.ids.value*.value == [3, 4]
        arguments.input.value.nested.value.id.value == null
        arguments.input.value.color.value.name == 'BLUE'
        arguments.ids.value*.value == [5]
        arguments.id.typeName == 'ID!'
        arguments.id.value.value == 6
    }

    def "external input objects convert back to literals including singleton lists enum values and nulls"() {
        given:
        def external = [id: 7, ids: [8, 9], nested: [id: null], color: 'BLUE']
        def type = schema.getType('Input')

        when:
        def literal = ValuesResolver.valueToLiteral(DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY,
                InputValueWithState.newExternalValue(external), type, context, Locale.ENGLISH)
        def internal = ValuesResolver.externalValueToInternalValue(DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY,
                external, type, context, Locale.ENGLISH)

        then:
        AstPrinter.printAstCompact(literal) == '{id:"7",ids:["8","9"],nested:{id:null},color:BLUE}'
        internal == [id: '7', ids: ['8', '9'], nested: [id: null], color: 'BLUE']
    }

    def "normalized non-null variables must already be valid"() {
        given:
        def operation = operation('query($id: ID!) { f(id: $id) }')

        when:
        ValuesResolver.getNormalizedVariableValues(schema, operation.variableDefinitions, RawVariables.of(raw), context, Locale.ENGLISH)

        then:
        thrown(AssertException)

        where:
        raw << [[:], [id: null]]
    }

    def "ID literal conversion accepts numeric IDs and UUID values"() {
        expect:
        graphql.Scalars.GraphQLID.coercing.valueToLiteral(123) instanceof StringValue
        graphql.Scalars.GraphQLID.coercing.valueToLiteral(123).value == '123'

        def uuid = UUID.fromString('a2608c81-9eaa-49bd-8b12-5640636f0c04')
        graphql.Scalars.GraphQLID.coercing.valueToLiteral(uuid, context, Locale.ENGLISH).value == uuid.toString()
    }

    private static OperationDefinition operation(String query) {
        Parser.parse(query).getDefinitionsOfType(OperationDefinition).first()
    }
}
