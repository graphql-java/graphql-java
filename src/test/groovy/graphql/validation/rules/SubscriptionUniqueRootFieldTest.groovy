package graphql.validation.rules

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class SubscriptionUniqueRootFieldTest extends Specification {
    def "5.2.3.1 subscription with only one root field passes validation"() {
        given:
        def subscriptionOneRoot = '''
            subscription doggo {
              dog {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionOneRoot)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with only one root field with fragment passes validation"() {
        given:
        def subscriptionOneRootWithFragment = '''
            subscription doggo {
              ...doggoFields
            }

            fragment doggoFields on SubscriptionRoot {
              dog {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionOneRootWithFragment)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with more than one root field fails validation"() {
        given:
        def subscriptionTwoRoots = '''
            subscription doggo {
              dog {
                name
              }
              cat {
                name
              }
            }
        '''
        when:
        def validationErrors = validate(subscriptionTwoRoots)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    def "5.2.3.1 subscription with more than one root field with fragment fails validation"() {
        given:
        def subscriptionTwoRootsWithFragment = '''
            subscription sub {
              ...multiplePets
            }

            fragment multiplePets on SubscriptionRoot {
              dog {
                name
              }
              cat {
                name
              }
            }
        '''
        when:
        def validationErrors = validate(subscriptionTwoRootsWithFragment)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    def "5.2.3.1 document can contain multiple operations with different root fields"() {
        given:
        def document = '''
            subscription catto {
              cat {
                name
              }
            }
            
            query doggo {
              dog {
                name
              }
            }
        '''
        when:
        def validationErrors = validate(document)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription root field must not be an introspection field"() {
        given:
        def subscriptionIntrospectionField = '''
            subscription doggo {
              __typename
            }
        '''
        when:
        def validationErrors = validate(subscriptionIntrospectionField)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionIntrospectionRootField
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document)
    }
}
