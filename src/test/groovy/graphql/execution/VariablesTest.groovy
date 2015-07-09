package graphql.execution

import graphql.Fixtures
import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.language.Type
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification


class ResolverTest extends Specification {

    Resolver variables = new Resolver()


    def "simple inputs"() {
        given:
        def schema = TestUtil.schemaWithInputType(inputType)
        VariableDefinition variableDefinition = new VariableDefinition("variable", variableType)
        when:
        def resolvedValues = variables.getVariableValues(schema, [variableDefinition], [variable: inputValue])
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
        def resolvedValues = variables.getVariableValues(schema, [variableDefinition], [variable: [name: 'a', id: 123]])
        then:
        resolvedValues['variable'] == [name: 'a', id: 123]
    }
}
