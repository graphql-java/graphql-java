package graphql.validation

import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaGenerator
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

    def "5.2.3.1 subscription with repeated same root field passes validation"() {
        given:
        def subscriptionRepeatedRootField = '''
            subscription doggo {
              dog {
                name
              }
              dog {
                nickname
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRepeatedRootField)

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

    def "5.2.3.1 subscription root field with #directive fails validation"() {
        given:
        def variableDefinition = directive.contains('$bool') ? '($bool: Boolean = true)' : ''
        def subscriptionRootDirective = """
            subscription doggo${variableDefinition} {
              dog ${directive} {
                name
              }
            }
        """

        when:
        def validationErrors = validate(subscriptionRootDirective)

        then:
        assertSingleSkipIncludeError(validationErrors, 1)

        where:
        directive << [
                '@skip(if: $bool)',
                '@include(if: $bool)',
                '@skip(if: true)',
                '@skip(if: false)',
                '@include(if: true)',
                '@include(if: false)'
        ]
    }

    def "5.2.3.1 subscription with skip and include root directives reports one error with both locations"() {
        given:
        def subscriptionRootDirectives = '''
            subscription doggo($bool: Boolean!) {
              dog @include(if: $bool) {
                name
              }
              cat @skip(if: $bool) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootDirectives)

        then:
        assertSingleSkipIncludeError(validationErrors, 2)
    }

    def "5.2.3.1 anonymous subscription with skip and include root directives fails validation"() {
        given:
        def anonymousSubscriptionRootDirectives = '''
            subscription ($bool: Boolean!) {
              dog @include(if: $bool) {
                name
              }
              cat @skip(if: $bool) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(anonymousSubscriptionRootDirectives)

        then:
        assertSingleSkipIncludeError(validationErrors, 2)
    }

    def "5.2.3.1 subscription root fragment spread must not use skip or include directives"() {
        given:
        def subscriptionRootFragmentSpreadDirective = '''
            subscription doggo($bool: Boolean!) {
              ...doggoFields @include(if: $bool)
            }

            fragment doggoFields on SubscriptionRoot {
              dog {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootFragmentSpreadDirective)

        then:
        assertSingleSkipIncludeError(validationErrors, 1)
    }

    def "5.2.3.1 subscription root fragment spread may use other directives"() {
        given:
        def subscriptionRootFragmentSpreadOtherDirective = '''
            subscription doggo {
              ...doggoFields @dogDirective
            }

            fragment doggoFields on SubscriptionRoot {
              dog {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootFragmentSpreadOtherDirective)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription root inline fragment must not use skip or include directives"() {
        given:
        def subscriptionRootInlineFragmentDirective = '''
            subscription doggo($bool: Boolean!) {
              ... @skip(if: $bool) {
                dog {
                  name
                }
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootInlineFragmentDirective)

        then:
        assertSingleSkipIncludeError(validationErrors, 1)
    }

    def "5.2.3.1 subscription root field reached through fragment must not use skip or include directives"() {
        given:
        def subscriptionRootFieldInFragmentDirective = '''
            subscription doggo($bool: Boolean!) {
              ...doggoFields
            }

            fragment doggoFields on SubscriptionRoot {
              dog @include(if: $bool) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootFieldInFragmentDirective)

        then:
        assertSingleSkipIncludeError(validationErrors, 1)
    }

    def "5.2.3.1 recursive subscription fragments do not loop and still report skip or include directives"() {
        given:
        def recursiveSubscriptionFragments = '''
            subscription doggo($bool: Boolean!) {
              ...doggoFields
            }

            fragment doggoFields on SubscriptionRoot {
              dog @skip(if: $bool) {
                name
              }
              ...doggoFields
            }
        '''

        when:
        def validationErrors = validate(recursiveSubscriptionFragments)

        then:
        skipIncludeErrors(validationErrors).size() == 1
        skipIncludeErrors(validationErrors)[0].locations.size() == 1
    }

    def "5.2.3.1 subscription root fragment with non matching object condition does not contribute fields"() {
        given:
        def nonMatchingRootFragment = '''
            subscription doggo {
              dog {
                name
              }
              ...dogFields
            }

            fragment dogFields on Dog {
              ...dogName
            }

            fragment dogName on Dog {
              name
            }
        '''

        when:
        def validationErrors = validate(nonMatchingRootFragment)

        then:
        !validationErrors.any { it.validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields }
        !skipIncludeErrors(validationErrors)
        validationErrors.any { it.validationErrorType == ValidationErrorType.InvalidFragmentType }
    }

    def "5.2.3.1 subscription root fragment with unknown condition does not contribute fields"() {
        given:
        def unknownRootFragment = '''
            subscription doggo {
              dog {
                name
              }
              ...unknownRootFields
            }

            fragment unknownRootFields on MissingRoot {
              cat {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(unknownRootFragment)

        then:
        !validationErrors.any { it.validationErrorType == ValidationErrorType.SubscriptionMultipleRootFields }
        !skipIncludeErrors(validationErrors)
        validationErrors.any { it.validationErrorType == ValidationErrorType.UnknownType }
    }

    def "5.2.3.1 subscription root fragment uses cached traversal summary when first seen below root"() {
        given:
        def fragmentFirstSeenBelowRoot = '''
            subscription doggo($bool: Boolean!) {
              dog {
                ...doggoFields
              }
              ...doggoFields
            }

            fragment doggoFields on SubscriptionRoot {
              cat @include(if: $bool) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(fragmentFirstSeenBelowRoot)

        then:
        skipIncludeErrors(validationErrors).size() == 1
        skipIncludeErrors(validationErrors)[0].locations.size() == 1
    }

    def "5.2.3.1 subscription subfields may use skip or include directives"() {
        given:
        def subscriptionSubfieldDirectives = '''
            subscription doggo($bool: Boolean!) {
              dog {
                name @skip(if: $bool)
                nickname @include(if: $bool)
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionSubfieldDirectives)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription fragment subfield inline fragments and fragment spreads may use skip or include directives"() {
        given:
        def subscriptionFragmentSubfieldDirectives = '''
            subscription doggo($bool: Boolean!) {
              ...rootFields
            }

            fragment rootFields on SubscriptionRoot {
              dog {
                ... @skip(if: $bool) {
                  name
                }
                ...dogFields @include(if: $bool)
              }
            }

            fragment dogFields on Dog {
              nickname
            }
        '''

        when:
        def validationErrors = validate(subscriptionFragmentSubfieldDirectives)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription subfield inline fragments and fragment spreads may use skip or include directives"() {
        given:
        def subscriptionSubfieldFragmentDirectives = '''
            subscription doggo($bool: Boolean!) {
              dog {
                ... @skip(if: $bool) {
                  name
                }
                ...dogFields @include(if: $bool)
              }
            }

            fragment dogFields on Dog {
              nickname
            }
        '''

        when:
        def validationErrors = validate(subscriptionSubfieldFragmentDirectives)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 query and mutation root fields may use skip or include directives"() {
        given:
        def queryAndMutationRootDirectives = '''
            query doggoQuery($bool: Boolean!) {
              dog @skip(if: $bool) {
                name
              }
              pet @include(if: $bool) {
                name
              }
            }

            mutation doggoMutation($bool: Boolean!) {
              createDog(input: {id: "1"}) @skip(if: $bool) {
                name
              }
              otherDog: createDog(input: {id: "2"}) @include(if: $bool) {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(queryAndMutationRootDirectives)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 query root inline fragments and fragment spreads may use skip or include directives"() {
        given:
        def queryRootFragmentDirectives = '''
            query doggoQuery($bool: Boolean!) {
              ... @skip(if: $bool) {
                dog {
                  name
                }
              }
              ...dogFields @include(if: $bool)
            }

            fragment dogFields on QueryRoot {
              pet {
                name
              }
            }
        '''

        when:
        def validationErrors = validate(queryRootFragmentDirectives)

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription root inline fragment can match implemented interface"() {
        given:
        def subscriptionRootInterfaceFragment = '''
            subscription events {
              ... on SubscriptionEvent {
                eventId
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootInterfaceFragment, abstractSubscriptionSchema())

        then:
        validationErrors.empty
    }

    def "5.2.3.1 subscription root inline fragment can match union containing subscription type"() {
        given:
        def subscriptionRootUnionFragment = '''
            subscription events {
              ... on SubscriptionUnion {
                __typename
              }
            }
        '''

        when:
        def validationErrors = validate(subscriptionRootUnionFragment, abstractSubscriptionSchema())

        then:
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionIntrospectionRootField
    }

    def "5.2.3.1 aliased subscription root introspection field fails validation"() {
        given:
        def subscriptionAliasedIntrospectionField = '''
            subscription doggo {
              typename: __typename
            }
        '''

        when:
        def validationErrors = validate(subscriptionAliasedIntrospectionField)

        then:
        !validationErrors.empty
        validationErrors.size() == 1
        validationErrors[0].validationErrorType == ValidationErrorType.SubscriptionIntrospectionRootField
        validationErrors[0].message == "Validation error (SubscriptionIntrospectionRootField) : Subscription operation 'doggo' root field '__typename' cannot be an introspection field"
    }

    static void assertSingleSkipIncludeError(List<ValidationError> validationErrors, int locationCount) {
        assert validationErrors.size() == 1
        assert validationErrors[0].validationErrorType == ValidationErrorType.ForbidSkipAndIncludeOnSubscriptionRoot
        assert validationErrors[0].message.contains("must not use @skip or @include directives in the top level selection")
        assert validationErrors[0].locations.size() == locationCount
    }

    static List<ValidationError> skipIncludeErrors(List<ValidationError> validationErrors) {
        return validationErrors.findAll { it.validationErrorType == ValidationErrorType.ForbidSkipAndIncludeOnSubscriptionRoot }
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }

    static List<ValidationError> validate(String query, GraphQLSchema schema) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(schema, document, Locale.ENGLISH)
    }

    static GraphQLSchema abstractSubscriptionSchema() {
        return SchemaGenerator.createdMockedSchema('''
            schema {
              query: Query
              subscription: SubscriptionRoot
            }

            interface SubscriptionEvent {
              eventId: ID
            }

            type SubscriptionRoot implements SubscriptionEvent {
              eventId: ID
              dog: Dog
            }

            union SubscriptionUnion = SubscriptionRoot

            type Dog {
              name: String
            }

            type Query {
              dog: Dog
            }
        ''')
    }
}
