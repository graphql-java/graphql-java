package graphql.validation.rules

import graphql.StarWarsSchema
import graphql.TestUtil
import graphql.language.ListType
import graphql.language.NonNullType
import graphql.language.TypeName
import graphql.language.VariableDefinition
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
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

    def "when a variable is of type GraphQLObjectType then it should not throw ClassCastException and validate with errors"() {
        setup:
        def schema = '''
            type User {
                id: String
            }
            
            input UserInput {
                id: String
            }
            
            type Mutation {
                createUser(user: UserInput): User
            }
            
            type Query {
                getUser: User
            }
        '''

        def query = '''
            mutation createUser($user: User){
                createUser(user: $user) {
                    id
                } 
            }
        '''

        def graphQlSchema = TestUtil.schema(schema)
        def document = TestUtil.parseQuery(query)
        def validator = new Validator()

        when:
        def validationErrors = validator.validateDocument(graphQlSchema, document)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors.validationErrorType as Set ==
                [ValidationErrorType.VariableTypeMismatch, ValidationErrorType.NonInputTypeOnVariable] as Set
    }
}
