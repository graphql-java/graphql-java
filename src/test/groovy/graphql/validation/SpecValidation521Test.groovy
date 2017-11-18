package graphql.validation
/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidation521Test extends SpecValidationBase {

    def '5.2.1 Field Selections on ... fieldNotDefined'() {
        def query = """
{
  dog {
    ... fieldNotDefined
  }
}
fragment fieldNotDefined on Dog {
  meowVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
    }

    def '5.2.1 Field Selections on ... aliasedLyingFieldTargetNotDefined'() {
        def query = """
{
  dog {
    ... aliasedLyingFieldTargetNotDefined
  }
}
fragment aliasedLyingFieldTargetNotDefined on Dog {
  barkVolume: kawVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
    }

    def '5.2.1 Field Selections on ... interfaceFieldSelection'() {
        def query = """
{
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
        validationErrors.empty
    }

    def '5.2.1 Field Selections on ... definedOnImplementorsButNotInterface'() {
        def query = """
{
  dog {
    ... definedOnImplementorsButNotInterface
  }
}
fragment definedOnImplementorsButNotInterface on Pet {
  nickname
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
    }

    def '5.2.1 Field Selections on ... inDirectFieldSelectionOnUnion'() {
        def query = """
{
  dog {
    ... inDirectFieldSelectionOnUnion
  }
}
fragment inDirectFieldSelectionOnUnion on CatOrDog {
  __typename
  ... on Pet {
    name
  }
  ... on Dog {
    barkVolume
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def '5.2.1 Field Selections on ... directFieldSelectionOnUnion'() {
        def query = """
{
  dog {
    ... directFieldSelectionOnUnion
  }
}
fragment directFieldSelectionOnUnion on CatOrDog {
  name
  barkVolume
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 2
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.FieldUndefined
        validationErrors.get(1).getValidationErrorType() == ValidationErrorType.FieldUndefined
    }
}
