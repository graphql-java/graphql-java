package graphql.validation

/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 *
 * This test checks that an inline fragment containing just a directive
 * is parsed correctly
 */
class SpecValidation282Test extends SpecValidationBase {

    def 'Inline fragment can omit type condition'() {
        def query = """
query {
  dog {
    name
    ... @skip(if: true) {
      barkVolume
    }
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

}
