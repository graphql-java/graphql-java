package graphql.validation
/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidation5421Test extends SpecValidationBase {

    def '5.4.2.1 Fragment spread target defined '() {
        def query = """
            query getDogName {
              dog {
                ... FragmentDoesNotExist
              }           
            }
        """
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.UndefinedFragment
    }
}
