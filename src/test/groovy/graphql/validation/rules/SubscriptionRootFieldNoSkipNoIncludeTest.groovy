package graphql.validation.rules

import graphql.parser.Parser
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationError
import graphql.validation.Validator
import spock.lang.Specification

class SubscriptionRootFieldNoSkipNoIncludeTest extends Specification {

    def "valid subscription with @skip and @include directives on subfields"() {
        given:
        def query = """
        subscription MySubscription(\$bool: Boolean = true) {
            dog {
                name @skip(if: \$bool)
                nickname @include(if: \$bool)
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def "invalid subscription with @skip directive on root field"() {
        given:
        def query = """
        subscription MySubscription(\$bool: Boolean = false) {
            dog @skip(if: \$bool) {
                name
            }
            dog @include(if: true) {
                nickname
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage().contains("Subscription operation 'MySubscription' root field 'dog' must not use @skip nor @include directives in top level selection")
    }

    def "invalid subscription with @include directive on root field"() {
        given:
        def query = """
        subscription MySubscription(\$bool: Boolean = true) {
            dog @include(if: \$bool) {
              name
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage().contains("Subscription operation 'MySubscription' root field 'dog' must not use @skip nor @include directives in top level selection")
    }

    def "invalid subscription with directive on root field in fragment spread"() {
        given:
        def query = """
        subscription MySubscription(\$bool: Boolean = false) {
            ...dogFragment
        }
        
        fragment dogFragment on SubscriptionRoot {
            dog @skip(if: \$bool) {
              name
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage().contains("Subscription operation 'MySubscription' root field 'dog' must not use @skip nor @include directives in top level selection")
    }

    def "invalid subscription with directive on root field in inline fragment"() {
        given:
        def query = """
        subscription MySubscription(\$bool: Boolean = true) {
            ... on SubscriptionRoot {
                dog @include(if: \$bool) {
                  name
                }
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage().contains("Subscription operation 'MySubscription' root field 'dog' must not use @skip nor @include directives in top level selection")
    }

    def "@skip and @include directives are valid on query root fields"() {
        given:
        def query = """
        query MyQuery {
            pet @skip(if: false) {
                name
            }
            pet @include(if: true) {
                name
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 0
    }

    def "@skip and @include directives are valid on mutation root fields"() {
        given:
        def query = """
        mutation MyMutation {
            createDog(input: {id: "a"}) @skip(if: false) {
                name
            }
            createDog(input: {id: "a"}) @include(if: true) {
                name
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 0
    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}
