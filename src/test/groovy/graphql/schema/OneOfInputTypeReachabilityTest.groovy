package graphql.schema

import graphql.Directives
import graphql.TestUtil
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

class OneOfInputTypeReachabilityTest extends Specification {

    def "inputTypeContainsOneOf resolves wrapped types and recursive references - #oneOf"() {
        given:
        def schema = TestUtil.schema("""
            input Choice ${oneOf ? '@oneOf' : ''} { value: String }
            input A { back: [B!], choice: [Choice!] }
            input B { back: A }
            input Plain { self: Plain, value: String }
            type Query { q(arg: A, plain: Plain): String }
        """)

        expect:
        schema.inputTypeContainsOneOf(schema.getType('A')) == oneOf
        schema.inputTypeContainsOneOf(schema.getType('B')) == oneOf
        schema.inputTypeContainsOneOf(schema.getType('Choice')) == oneOf
        schema.inputTypeContainsOneOf(nonNull(list(nonNull(schema.getType('A'))))) == oneOf
        schema.inputTypeContainsOneOf(nonNull(list(typeRef('B')))) == oneOf
        !schema.inputTypeContainsOneOf(schema.getType('Plain'))
        !schema.inputTypeContainsOneOf(GraphQLString)

        where:
        oneOf << [false, true]
    }

    def "inputTypeContainsOneOf is independent for schemas with the same type names"() {
        given:
        def plain = TestUtil.schema('input Choice { value: String } type Query { q(arg: Choice): String }')
        def oneOf = TestUtil.schema('input Choice @oneOf { value: String } type Query { q(arg: Choice): String }')

        expect:
        !plain.inputTypeContainsOneOf(plain.getType('Choice'))
        oneOf.inputTypeContainsOneOf(oneOf.getType('Choice'))
        plain.inputTypeContainsOneOf(oneOf.getType('Choice'))
        oneOf.inputTypeContainsOneOf(plain.getType('Choice'))
        plain.inputTypeContainsOneOf(typeRef('Unknown'))
        plain.inputTypeContainsOneOf(newInputObject().name('Unknown').field(newInputObjectField().name('value').type(GraphQLString)).build())
    }

    def "inputTypeContainsOneOf follows schema transforms"() {
        given:
        def original = TestUtil.schema('input Choice { value: String } type Query { q(arg: Choice): String }')
        def choice = (GraphQLInputObjectType) original.getType('Choice')
        def changedChoice = choice.transform { it.withAppliedDirective(Directives.OneOfDirective.toAppliedDirective()) }
        def changedQuery = original.queryType.transform {
            it.field(original.queryType.getFieldDefinition('q').transform { field ->
                field.argument(newArgument().name('arg').type(changedChoice))
            })
        }

        when:
        def transformed = original.transform { it.query(changedQuery) }
        def registryOnly = transformed.transformWithoutTypes { it.codeRegistry(GraphQLCodeRegistry.newCodeRegistry().build()) }

        then:
        !original.inputTypeContainsOneOf(choice)
        transformed.inputTypeContainsOneOf(transformed.getType('Choice'))
        registryOnly.inputTypeContainsOneOf(registryOnly.getType('Choice'))
        !original.transformWithoutTypes { it.description('copy') }.inputTypeContainsOneOf(choice)
    }

    def "inputTypeContainsOneOf works with FastBuilder and unresolved references - #oneOf"() {
        given:
        def choiceBuilder = newInputObject().name('Choice').field(newInputObjectField().name('value').type(GraphQLString))
        if (oneOf) {
            choiceBuilder.withAppliedDirective(Directives.OneOfDirective.toAppliedDirective())
        }
        def choice = choiceBuilder.build()
        def recursive = newInputObject().name('Recursive')
                .field(newInputObjectField().name('self').type(typeRef('Recursive')))
                .field(newInputObjectField().name('choice').type(typeRef('Choice'))).build()
        def query = newObject().name('Query').field(newFieldDefinition().name('q').type(GraphQLString)
                .argument(newArgument().name('arg').type(typeRef('Recursive')))).build()

        when:
        def schema = new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), query, null, null)
                .addType(recursive).addType(choice).withValidation(true).build()

        then:
        schema.inputTypeContainsOneOf(recursive) == oneOf
        schema.inputTypeContainsOneOf(typeRef('Choice')) == oneOf

        where:
        oneOf << [false, true]
    }
}
