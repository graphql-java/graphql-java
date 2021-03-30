package graphql.validation.rules

import graphql.language.SourceLocation
import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

import static graphql.validation.rules.SingleSubscriptionFieldOnly.moreThanOneSubscriptionMessage

class SingleSubscriptionFieldOnlyTest extends Specification {

    def '5.2.3.1 Subscription operations must have exactly one root field.'() {
        def query = """
        subscription getName {
            onDogUpdate {
                name
            }
            onCatUpdate {
                name
            }
        }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0] == moreThanOneSubscription("getName", 2, 1)
    }

    def '5.2.3.1 Can have multiple subscription operations'() {
        def query = """
        subscription dogOperation {
            onDogUpdate {
                name
            }
        }

        subscription catOperation {
            onCatUpdate {
                id
            }
        }
        """.stripIndent()
        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    ValidationError moreThanOneSubscription(String defName, int line, int column) {
        return new ValidationError(ValidationErrorType.MoreThanOneSubscriptionField,
                [new SourceLocation(line, column)],
                moreThanOneSubscriptionMessage(defName))
    }

    List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)
    }
}
