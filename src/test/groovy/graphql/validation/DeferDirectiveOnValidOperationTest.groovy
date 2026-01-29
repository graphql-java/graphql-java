package graphql.validation

import graphql.ExperimentalApi
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.OperationValidationRule
import graphql.validation.OperationValidator
import graphql.validation.SpecValidationSchema
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DeferDirectiveOnValidOperationTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(SpecValidationSchema.specValidationSchema, document, i18n)
        validationContext.getGraphQLContext().put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector,
                { rule -> rule == OperationValidationRule.DEFER_DIRECTIVE_ON_VALID_OPERATION }))
    }

    def "Allow simple defer on query with fragment definition"() {
        def query = '''
            query {
                dog {
                    ... DogFields @defer
                }
            }

            fragment DogFields on Dog {
                name
            }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "Allow simple defer on mutation with fragment definition"() {
        def query = '''
            mutation {
                createDog(input: {name: "Fido"}) {
                    ... DogFields @defer
                }
            }

            fragment DogFields on Dog {
                name
            }
        '''

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "Not allow defer on subscription operation"() {
        given:
        def query = """
            subscription pets {
                dog {
                    ... @defer {
                        name
                    }
                    nickname
                }
            }
        """


        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }


    def "Allow defer(if:false) on subscription operation"() {
        given:
        def query = """
            subscription pets {
                dog {
                    ... @defer(if:false) {
                        name
                    }
                    nickname
                }
            }
        """


        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "Not allow simple defer on subscription with fragment definition"() {
        def query = '''
            subscription {
                dog {
                    ... DogFields @defer
                }
            }

            fragment DogFields on Dog {
                name
            }
        '''

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }

    def "Not allow defer on fragment when operation is subscription"() {
        given:
        def query = """
            fragment doggo on PetMutationType {
                ... {
                    dog {
                        ... @defer {
                            id
                        }
                        nickname
                    }

                }
            }

            subscription doggoMutation {
                ...{
                    ...doggo
                }
           }


        """
        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }

    def "Allow defer(if:false) on fragment when operation is subscription"() {
        given:
        def query = """
            fragment doggo on PetMutationType {
                ... {
                    dog {
                        ... @defer(if:false) {
                            id
                        }
                        nickname
                    }

                }
            }

            subscription doggoMutation {
                ...{
                    ...doggo
                }
           }


        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "Not allow defer subscription even when there are multiple operations with multiple fragments"() {
        given:
        def query = """

          fragment doggoSubscription on SubscriptionRoot {
                ... {
                    dog {
                        ...doggo
                    }
                }
            }

            query pets {
                ... @defer {
                    dog {
                        name
                    }
                }
            }

            subscription pets2 {
                   ...doggoSubscription
            }

            query pets3 {
                dog {
                    name
                }
            }

            fragment doggo on Dog{
                ... @defer {
                    name
                }
            }
        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.errors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[doggoSubscription/dog/doggo]) : Directive 'defer' is not allowed to be used on operation subscription"

    }


    def "Not allow defer subscription even when there are multiple operations and multiple fragments"() {
        given:
        def query = """
            query pets {
                ... @defer {
                    dog {
                        name
                    }
                }
            }

            subscription pets2 {
                dog {
                    ... @defer {
                        name
                    }
                }
            }


        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.errors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[dog]) : Directive 'defer' is not allowed to be used on operation subscription"

    }

    def "Allows defer on mutation when it is not on root level"() {
        given:
        def query = """
            mutation pets {
                dog {
                    ... @defer {
                        name
                    }
                }
            }
        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }


    def "Allow defer on subscription when defer(if == false) "() {
        given:
        def query = """
            subscription pets{
                dog {
                    ... @defer(if:false) {
                        name
                    }
                    nickname
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }

    def "Not allow defer on subscription when defer(if == true) "() {
        given:
        def query = """
            subscription pets{
                dog {
                    ... @defer(if:true) {
                        name
                        }
                    nickname
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[dog]) : Directive 'defer' is not allowed to be used on operation subscription"


    }

    def "Allow defer when if is variable that could have false as value "() {
        given:
        def query = """
            subscription pets(\$ifVar:Boolean){
                dog {
                    ... @defer(if:\$ifVar) {
                        name
                    }
                    nickname
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }



}
