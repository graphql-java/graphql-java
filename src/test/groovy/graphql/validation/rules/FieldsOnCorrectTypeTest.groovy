package graphql.validation.rules

import graphql.language.Field
import graphql.schema.GraphQLObjectType
import graphql.validation.ErrorCollector
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class FieldsOnCorrectTypeTest extends Specification {

    ErrorCollector errorCollector = new ErrorCollector()
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
        errorCollector.containsError(ValidationErrorType.FieldUndefined)

    }
}
