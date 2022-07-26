package graphql.validation
/**
 * validation examples used in the spec in given section
 * http://facebook.github.io/graphql/#sec-Validation
 * @author dwinsor
 *
 */
class SpecValidation51Test extends SpecValidationBase {

    def '5.1.1.1 Operation Name Uniqueness Valid'() {
        def query = """
query getDogName {
  dog {
    name
  }
}

query getOwnerName {
  dog {
    owner {
      name
    }
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def '5.1.1.1 Operation Name Uniqueness Not Valid'() {
        def query = """
query getName {
  dog {
    name
  }
}

query getName {
  dog {
    owner {
      name
    }
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
    }

    def '5.1.1.1 Operation Name Uniqueness Not Valid Different Operations'() {
        def query = """
query dogOperation {
  dog {
    name
  }
}

mutation dogOperation {
  mutateDog {
    id
  }
}
"""
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
    }
}
