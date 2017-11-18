package graphql.validation
/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidation562Test extends SpecValidationBase {

    def 'Directives Are In Valid Locations -- Skip query'() {
        def query = """
query @skip(if: false) {
  dog {
    ... interfaceFieldSelection
  }
}
fragment interfaceFieldSelection on Pet {
  name
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
    }

    def 'Directives Are In Valid Locations -- Skip frag def'() {
        def query = """
query {
  dog {
    ... interfaceFieldSelection
  }
}
fragment interfaceFieldSelection on Pet @skip(if: false) {
  name
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
    }

    def 'Directives Are In Valid Locations -- Skip inline frag def'() {
        def query = """
query {
  dog {
    ...on Pet @skip(if: false) {
        name
    }
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
        validationErrors.size() == 0
    }
}
