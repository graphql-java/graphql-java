package graphql.validation.rules


import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator

class UniqueOperationNamesTest extends ValidationRuleTest {

    def '5.1.1.1 Operation Name Uniqueness Not Valid'() {
        def query = """\
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
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateOperationName
    }

    def '5.1.1.1 Operation Name Uniqueness Not Valid Different Operations'() {
        def query = """\
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
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateOperationName
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.getDefault())
    }
}
