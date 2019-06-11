package graphql.validation.rules

import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.schema.GraphQLNonNull
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLNonNull.nonNull

class VariableDefaultValuesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariableDefaultValuesOfCorrectType defaultValuesOfCorrectType = new VariableDefaultValuesOfCorrectType(validationContext, errorCollector)

    def "NonNull type, but with default value"() {
        given:
        GraphQLNonNull nonNullType = nonNull(GraphQLString)
        StringValue defaultValue = StringValue.newStringValue("string").build()
        VariableDefinition variableDefinition = VariableDefinition.newVariableDefinition("var", TypeName.newTypeName("String").build(), defaultValue).build()
        validationContext.getInputType() >> nonNullType
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.DefaultForNonNullArgument)

    }

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
