package graphql.execution

import graphql.Scalars
import graphql.TestUtil
import graphql.language.Argument
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLFieldArgument
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import spock.lang.Specification


class ResolverTest extends Specification {

    Resolver resolver = new Resolver()


    def "simple inputs"() {
        given:
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", variableType)
        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: inputValue])
        then:
        resolvedValues['variable'] == outputValue

        where:
        inputType             | variableType           | inputValue   || outputValue
        Scalars.GraphQLInt    | new TypeName("Int")    | 100          || 100
        Scalars.GraphQLString | new TypeName("String") | 'someString' || 'someString'

    }

    def "object as input"() {
        given:
        def nameField = new GraphQLInputObjectField("name", Scalars.GraphQLString)
        def idField = new GraphQLInputObjectField("id", Scalars.GraphQLInt)
        def inputType = new GraphQLInputObjectType("Person", [nameField, idField])
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", new TypeName("Person"))

        when:
        def resolvedValues = resolver.getVariableValues(schema, [variableDefinition], [variable: [name: 'a', id: 123]])
        then:
        resolvedValues['variable'] == [name: 'a', id: 123]
    }

    def "resolves argument with variable reference"(){
        given:
        def variables = [var:'hello']
        def fieldArgument = new GraphQLFieldArgument("arg", Scalars.GraphQLString)
        def argument = new Argument("arg",new VariableReference("var"))

        when:
        def values = resolver.getArgumentValues([fieldArgument], [argument], variables)

        then:
        values['arg'] == 'hello'
    }
}
