package graphql.validation
/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 *
 */
class SpecValidation573Test extends SpecValidationBase {

    def '5.7.3 Variables Are Input Types - type mismatch (must be non-null)'() {
        def query = """
query madDog(\$dogCommand: DogCommand){
    dog {
        doesKnowCommand(dogCommand: \$dogCommand)
    }
}"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.VariableTypeMismatch
    }

    def '5.7.3 Variables Are Input Types - unknown type'() {
        def query = """
query madDog(\$dogCommand: UnknownType){
    dog {
        doesKnowCommand(dogCommand: \$dogCommand)
    }
}"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
    }

    def '5.7.3 Variables Are Input Types - non-null unknown type'() {
        def query = """
query madDog(\$dogCommand: UnknownType!){
    dog {
        doesKnowCommand(dogCommand: \$dogCommand)
    }
}"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
    }

    def '5.7.3 Variables Are Input Types - non-null list unknown type'() {
        def query = """
query madDog(\$dogCommand: [UnknownType]){
    dog {
        doesKnowCommand(dogCommand: \$dogCommand)
    }
}"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UnknownType
    }
}
