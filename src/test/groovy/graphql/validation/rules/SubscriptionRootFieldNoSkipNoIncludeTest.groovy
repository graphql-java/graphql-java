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
        subscription MySubscription {
            dog @include(if: true) {
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

    // dz todo investigate NPE with field collector on spreads at root level
    def "invalid subscription with directive in fragment spread"() {
        given:
        def query = """
        subscription MySubscription {
            ...dogFragment @skip(if: false)
        }
        
        fragment dogFragment on Subscription {
            dog {
              name
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage() == "Subscription root field cannot have @skip directive."
    }

    // dz todo investigate NPE with field collector on spreads at root level
    def "invalid subscription with directive in inline fragment"() {
        given:
        def query = """
        subscription MySubscription {
            ... on Subscription @include(if: true) {
                dog {
                  name
                }
            }
        }
        """

        when:
        def validationErrors = validate(query)

        then:
        validationErrors.size() == 1
        validationErrors.first().getMessage() == "Subscription root fields cannot have @include directive."
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
