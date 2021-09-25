package graphql.validation

class MaxValidationErrorsTest extends SpecValidationBase {

    def "The maximum number of validation messages is respected"() {
        def directives = "@lol" * 500
        def query = """
            query lotsOfErrors {
              f $directives        
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 100

        when: "we can set a new maximum"
        Validator.setMaxValidationErrors(10)
        validationErrors = validate(query)

        then:
        validationErrors.size() == 10
    }
}
