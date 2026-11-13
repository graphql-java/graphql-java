package graphql.validation

import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class UniqueOperationNamesTest extends Specification {

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
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateOperationName
        validationErrors[0].locations == [new SourceLocation(8, 1)]
        validationErrors[0].message == "Validation error (DuplicateOperationName) : There can be only one operation named 'getName'"
    }

    def '5.1.1.1 Operation Name Uniqueness Not Valid Different Operations'() {
        def query = """
        query dogOperation {
            dog {
                name
            }
        }

        mutation dogOperation {
            createDog(input: {id: "1"}) {
                name
            }
        }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.DuplicateOperationName
        validationErrors[0].locations == [new SourceLocation(8, 1)]
        validationErrors[0].message == "Validation error (DuplicateOperationName) : There can be only one operation named 'dogOperation'"
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
