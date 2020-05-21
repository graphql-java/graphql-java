package graphql.validation.rules

import graphql.language.BooleanValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class VariableDefaultValuesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariableDefaultValuesOfCorrectType defaultValuesOfCorrectType = new VariableDefaultValuesOfCorrectType(validationContext, errorCollector)

    def "default value has wrong type"() {
        given:
        BooleanValue defaultValue = BooleanValue.newBooleanValue(false).build()
        VariableDefinition variableDefinition = VariableDefinition.newVariableDefinition("var", TypeName.newTypeName("String").build(), defaultValue).build()
        validationContext.getInputType() >> GraphQLString
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.BadValueForDefaultArg)
    }
}
