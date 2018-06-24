package graphql.validation.rules

import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.validation.rules.UniqueOperationNames.duplicateOperationNameMessage

class UniqueOperationNamesTest extends Specification {

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
        validationErrors[0] == duplicateOperationName("getName", 7, 1)
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
        validationErrors[0] == duplicateOperationName("dogOperation", 7, 1)
    }

    ValidationError duplicateOperationName(String defName, int line, int column) {
        return new ValidationError(ValidationErrorType.DuplicateOperationName,
                [new SourceLocation(line, column)],
                duplicateOperationNameMessage(defName))
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)
    }
}
