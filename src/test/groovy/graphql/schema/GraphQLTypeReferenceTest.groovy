package graphql.schema

import graphql.Scalars
import spock.lang.Specification

class GraphQLTypeReferenceTest extends Specification {

    def "the same reference can be used multiple times without throwing exception"() {
        when:
        GraphQLTypeReference ref = new GraphQLTypeReference("String")
        def inputObject = GraphQLInputObjectType.newInputObject()
                .name("ObjInput")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("value")
                .type(ref)) //Will get replaced, as expected
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("value2")
                .type(ref)) //Will get replaced, as expected
                .build()

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                .name("test")
                .type(Scalars.GraphQLString)
                .argument(GraphQLArgument.newArgument()
                .name("in")
                .type(inputObject))
        )).build()

        then:
        // issue 1216 - reuse of type reference caused problems
        GraphQLInputObjectType objInput = ((GraphQLInputObjectType)schema.getType("ObjInput"))
        objInput.getField("value").getType() != ref
        objInput.getField("value2").getType() != ref
    }
}
