package graphql.validation.rules

import graphql.language.Field
import graphql.schema.GraphQLObjectType
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class FieldsOnCorrectTypeTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    ValidationContext validationContext = Mock(ValidationContext)
    FieldsOnCorrectType fieldsOnCorrectType = new FieldsOnCorrectType(validationContext, errorCollector)


    def "field undefined"() {
        given:
        def parentType = GraphQLObjectType.newObject().name("parentType").build()
        validationContext.getParentType() >> parentType
        validationContext.getFieldDef() >> null
        def field = new Field("name")
        when:
        fieldsOnCorrectType.checkField(field)

        then:
        errorCollector.containsValidationError(ValidationErrorType.FieldUndefined)

    }
}
