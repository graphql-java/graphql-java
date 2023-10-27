package graphql.validation.rules

import graphql.GraphQLContext
import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.BooleanValue
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.Scalars.GraphQLString

class VariableDefaultValuesOfCorrectTypeTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()
    VariableDefaultValuesOfCorrectType defaultValuesOfCorrectType = new VariableDefaultValuesOfCorrectType(validationContext, errorCollector)

    void setup() {
        def context = GraphQLContext.getDefault()
        validationContext.getGraphQLContext() >> context
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

    def "default value has wrong type with error message"() {
        setup:
        def schema = '''
            type User {
                id: String
            }
            
            type Query {
                getUsers(howMany: Int) : [User]
            }
        '''

        def query = '''
            query($howMany: Int = "NotANumber") {
                getUsers(howMany: $howMany) {
                    id
                } 
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document, Locale.ENGLISH)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].getValidationErrorType() == ValidationErrorType.BadValueForDefaultArg
        validationErrors[0].message == "Validation error (BadValueForDefaultArg) : Bad default value 'StringValue{value='NotANumber'}' for type 'Int'"
    }
}