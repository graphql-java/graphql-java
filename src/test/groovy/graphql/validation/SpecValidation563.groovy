package graphql.validation

class SpecValidation563 extends SpecValidationBase {

    def "Input Object Field Uniqueness Valid"() {
        def query = """
            query getDogName {
              dog(arg1:"argValue") {
                  name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "Input Object Field Uniqueness Invalid"() {
        def query = """
            query getDogName {
              dog(arg1:"value1",arg1:"value2") {
                  name
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.DuplicateInputField
    }
}
