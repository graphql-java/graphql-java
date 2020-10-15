package graphql.schema.validation

import graphql.schema.GraphQLObjectType
import spock.lang.Specification

import static SchemaValidationErrorType.ObjectDoesNotContainsAnyField
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class ObjectTypeWithNoFieldTest extends Specification {

    def "object type with no fields"() {
        given: "type with no fields"
        GraphQLObjectType noFieldObjType = GraphQLObjectType.newObject()
                .name("obj")
                .build()

        and: "a errorCollector"
        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        when: "a check if the type is valid"
        new ObjectTypeWithNoField().check(noFieldObjType, errorCollector)

        then: "errorCollector contains ObjectDoesNotContainsAnyField error"
        errorCollector.containsValidationError(ObjectDoesNotContainsAnyField)
        errorCollector.getErrors() == [
                new SchemaValidationError(ObjectDoesNotContainsAnyField,
                        "obj type does not contains any fields")
        ]
    }

    def "object type with a field"() {
        given: "type with one field"
        GraphQLObjectType noFieldObjType = GraphQLObjectType.newObject()
                .name("obj")
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .build()

        and: "a errorCollector"
        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

        when: "a check if the type is valid"
        new ObjectTypeWithNoField().check(noFieldObjType, errorCollector)

        then: "errorCollector dosen't contains any error"
        !errorCollector.containsValidationError(ObjectDoesNotContainsAnyField)
        errorCollector.getErrors().isEmpty()
    }

}
