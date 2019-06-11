package graphql.schema

import graphql.Scalars
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification
import spock.lang.Unroll

class GraphQLTypeVisitorStubTest extends Specification {


    @Unroll
    def "#visitMethod scalar type"() {
        given:
        GraphQLTypeVisitorStub typeVisitorStub = Spy(GraphQLTypeVisitorStub, constructorArgs: [])
        TraverserContext context = Mock(TraverserContext)

        when:
        def control = typeVisitorStub."$visitMethod"(node, context)
        then:
        typeVisitorStub.visitGraphQLType(node, context) >> TraversalControl.CONTINUE
        control == TraversalControl.CONTINUE

        where:
        node                                | visitMethod
        Mock(GraphQLScalarType)             | 'visitGraphQLScalarType'
        Mock(GraphQLArgument)               | 'visitGraphQLArgument'
        Mock(GraphQLInterfaceType)          | 'visitGraphQLInterfaceType'
        Mock(GraphQLEnumType)               | 'visitGraphQLEnumType'
        Mock(GraphQLEnumValueDefinition)    | 'visitGraphQLEnumValueDefinition'
        Mock(GraphQLFieldDefinition)        | 'visitGraphQLFieldDefinition'
        Mock(GraphQLInputObjectField)       | 'visitGraphQLInputObjectField'
        Mock(GraphQLInputObjectType)        | 'visitGraphQLInputObjectType'
        Mock(GraphQLList)                   | 'visitGraphQLList'
        Mock(GraphQLNonNull)                | 'visitGraphQLNonNull'
        Mock(GraphQLObjectType)             | 'visitGraphQLObjectType'
        Mock(GraphQLTypeReference)          | 'visitGraphQLTypeReference'
        Mock(GraphQLUnionType)              | 'visitGraphQLUnionType'
    }
}
