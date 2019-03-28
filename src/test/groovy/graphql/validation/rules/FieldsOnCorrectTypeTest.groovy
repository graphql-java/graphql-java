package graphql.validation.rules

import graphql.language.Field
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector

import static graphql.validation.ValidationErrorType.FieldUndefined

class FieldsOnCorrectTypeTest extends ValidationRuleTest {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = mockValidationContext()
    FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext, errorCollector)


    def "should add error to collector when field definition is null"() {
        given:
        def parentType = GraphQLObjectType.newObject().name("parentType").build()
        validationContext.getParentType() >> parentType
        validationContext.getFieldDef() >> null
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.containsValidationError(FieldUndefined, "Field 'name' in type 'parentType' is undefined")
        errorCollector.errors.size() == 1
    }

    def "should results in no error when field definition is filled"() {
        given:
        def parentType = GraphQLObjectType.newObject().name("parentType").build()
        validationContext.getParentType() >> parentType
        validationContext.getFieldDef() >> Mock(GraphQLFieldDefinition)
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.errors.isEmpty()
    }

    def "should results in no error when parent type is null"() {
        given:
        validationContext.getParentType() >> null
        def field = new Field("name")

        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.errors.isEmpty()
    }
}
