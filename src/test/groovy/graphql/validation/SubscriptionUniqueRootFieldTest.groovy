package graphql.validation

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
            subscription pets {
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
        validationErrors[0].message == "Validation error (SubscriptionMultipleRootFields) : Subscription operation 'pets' must have exactly one root field"
    }

    def "5.2.3.1 subscription with more than one root field with fragment fails validation"() {
        given:
        def subscriptionTwoRootsWithFragment = '''
            subscription whoIsAGoodBoy {
              ...pets
            }

            fragment pets on SubscriptionRoot {
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
        validationErrors[0].message == "Validation error (SubscriptionMultipleRootFields) : Subscription operation 'whoIsAGoodBoy' must have exactly one root field"
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
        validationErrors[0].message == "Validation error (SubscriptionIntrospectionRootField) : Subscription operation 'doggo' root field '__typename' cannot be an introspection field"
    }

    def "5.2.3.1 subscription root field via fragment must not be an introspection field"() {
        given:
        def subscriptionIntrospectionField = '''
            subscription doggo {
              ...dogs
            }
            
            fragment dogs on SubscriptionRoot {
              __typename
            }
        '''
        when:
        def validationErrors = validate(subscriptionIntrospectionField)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionIntrospectionRootField
        validationErrors[0].message == "Validation error (SubscriptionIntrospectionRootField) : Subscription operation 'doggo' root field '__typename' cannot be an introspection field"
    }

    def "5.2.3.1 subscription with multiple root fields within inline fragment are not allowed"() {
        given:
        def subscriptionOneRootWithFragment = '''
            subscription doggo {             
                ... {
                    dog {
                        name
                    }
                    cat {
                        name
                    }
                }
            }
        '''

        when:
        def validationErrors = validate(subscriptionOneRootWithFragment)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
        validationErrors[0].message == "Validation error (SubscriptionMultipleRootFields) : Subscription operation 'doggo' must have exactly one root field"
    }


    def "5.2.3.1 subscription with more than one root field with multiple fragment fails validation"() {
        given:
        def subscriptionTwoRootsWithFragment = '''
            fragment doggoRoot on SubscriptionRoot {
               ...doggoLevel1
            }                
            
            fragment doggoLevel1 on SubscriptionRoot {
                ...doggoLevel2
            }   
            
            fragment doggoLevel2 on SubscriptionRoot {
                dog {
                    name
                }
                cat {
                    name
                }
            }
            
            subscription whoIsAGoodBoy {
              ...doggoRoot
           }
        '''
        when:
        def validationErrors = validate(subscriptionTwoRootsWithFragment)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
        validationErrors[0].message == "Validation error (SubscriptionMultipleRootFields) : Subscription operation 'whoIsAGoodBoy' must have exactly one root field"
    }


    def "5.2.3.1 subscription with more than one root field with multiple fragment with inline fragments fails validation"() {
        given:
        def subscriptionTwoRootsWithFragment = '''
            fragment doggoRoot on SubscriptionRoot {
               ...doggoLevel1
            }                
            
            fragment doggoLevel1 on SubscriptionRoot {
                ...{
                    ...doggoLevel2
                }
            }   
            
            fragment doggoLevel2 on SubscriptionRoot {
                ...{
                    dog {
                        name
                    }
                    cat {
                        name
                    }
                }
            }
            
            subscription whoIsAGoodBoy {
              ...doggoRoot
           }
        '''
        when:
        def validationErrors = validate(subscriptionTwoRootsWithFragment)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
        validationErrors[0].message == "Validation error (SubscriptionMultipleRootFields) : Subscription operation 'whoIsAGoodBoy' must have exactly one root field"
    }


    def "5.2.3.1 subscription with one root field with multiple fragment with inline fragments does not fail validation"() {
        given:
        def subscriptionTwoRootsWithFragment = '''
            fragment doggoRoot on SubscriptionRoot {
               ...doggoLevel1
            }                
            
            fragment doggoLevel1 on SubscriptionRoot {
                ...{
                    ...doggoLevel2
                }
            }   
            
            fragment doggoLevel2 on SubscriptionRoot {
                ...{
                    dog {
                        name
                    }
                 
                }
            }
            
            subscription whoIsAGoodBoy {
              ...doggoRoot
           }
        '''
        when:
        def validationErrors = validate(subscriptionTwoRootsWithFragment)

        then:
        validationErrors.empty
    }
    def "5.2.3.1 subscription with two root fields where one has @skip with literal true passes validation"() {
        given:
        def query = '''
            subscription doggo {
              dog @skip(if: true) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with two root fields where one has @include with literal false passes validation"() {
        given:
        def query = '''
            subscription doggo {
              dog @include(if: false) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with two root fields where one has @skip with variable fails validation"() {
        given:
        def query = '''
            subscription doggo($shouldSkip: Boolean!) {
              dog @skip(if: $shouldSkip) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    def "5.2.3.1 subscription with two root fields where one has @include with variable fails validation"() {
        given:
        def query = '''
            subscription doggo($shouldInclude: Boolean!) {
              dog @include(if: $shouldInclude) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    def "5.2.3.1 subscription with one root field with @skip variable passes validation"() {
        given:
        def query = '''
            subscription doggo($shouldSkip: Boolean!) {
              dog @skip(if: $shouldSkip) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with one root field with @include variable passes validation"() {
        given:
        def query = '''
            subscription doggo($shouldInclude: Boolean!) {
              dog @include(if: $shouldInclude) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription with two root fields where one has @skip with literal false fails validation"() {
        given:
        def query = '''
            subscription doggo {
              dog @skip(if: false) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    def "5.2.3.1 subscription with two root fields in fragment where one has @skip with variable fails validation"() {
        given:
        def query = '''
            subscription doggo($shouldSkip: Boolean!) {
              ...fields
            }

            fragment fields on SubscriptionRoot {
              dog @skip(if: $shouldSkip) {
                name
              }
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
