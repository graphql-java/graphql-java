package graphql.schema

import graphql.Scalars
import spock.lang.Specification

import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema

class GraphQLTypeReferenceTest extends Specification {

    def "the same reference can be used multiple times without throwing exception"() {
        when:
        GraphQLTypeReference ref = new GraphQLTypeReference("String")
        def inputObject = newInputObject()
                .name("ObjInput")
                .field(newInputObjectField()
                .name("value")
                .type(ref)) //Will get replaced, as expected
                .field(newInputObjectField()
                .name("value2")
                .type(ref)) //Will get replaced, as expected
                .build()

        GraphQLSchema schema = newSchema()
                .query(
                newObject()
                        .name("Query")
                        .field(newFieldDefinition()
                        .name("test")
                        .type(Scalars.GraphQLString)
                        .argument(newArgument()
                        .name("in")
                        .type(inputObject))
                )).build()

        then:
        // issue 1216 - reuse of type reference caused problems
        schema != null
        GraphQLInputObjectType objInput = ((GraphQLInputObjectType) schema.getType("ObjInput"))
        objInput.getField("value").getType() != ref
        objInput.getField("value").getType() instanceof GraphQLScalarType
        objInput.getField("value2").getType() != ref
        objInput.getField("value2").getType() instanceof GraphQLScalarType
    }
}
