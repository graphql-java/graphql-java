package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class VariablesAreInputTypesTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariablesAreInputTypes variablesAreInputTypes = new VariablesAreInputTypes(validationContext, errorCollector)


    def "the unmodified ast type is not a schema input type"() {
        given:
        def astType = new NonNullType(new ListType(new TypeName(StarWarsSchema.droidType.getName())))
        VariableDefinition variableDefinition = new VariableDefinition("var", astType)
        validationContext.getSchema() >> StarWarsSchema.starWarsSchema

        when:
        variablesAreInputTypes.checkVariableDefinition(variableDefinition)

        then:
        errorCollector.containsValidationError(ValidationErrorType.NonInputTypeOnVariable)
    }
}
