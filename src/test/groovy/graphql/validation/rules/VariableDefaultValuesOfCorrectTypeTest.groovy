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

class VariableDefaultValuesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariableDefaultValuesOfCorrectType defaultValuesOfCorrectType = new VariableDefaultValuesOfCorrectType(validationContext, errorCollector)

    def "NonNull type, but with default value"() {
        given:
        GraphQLNonNull nonNullType = new GraphQLNonNull(GraphQLString)
        StringValue defaultValue = new StringValue("string")
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"), defaultValue)
        validationContext.getInputType() >> nonNullType
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.DefaultForNonNullArgument)

    }

    def "default value has wrong type"(){
        given:
        BooleanValue defaultValue = new BooleanValue(false)
        VariableDefinition variableDefinition = new VariableDefinition("var", new TypeName("String"), defaultValue)
        validationContext.getInputType() >> GraphQLString
        when:
        defaultValuesOfCorrectType.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.BadValueForDefaultArg)
    }
}
