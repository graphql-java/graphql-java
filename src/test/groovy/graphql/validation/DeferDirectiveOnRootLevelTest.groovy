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

class DeferDirectiveOnRootLevelTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(
                SpecValidationSchema.specValidationSchema,
                document,
                I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH))
        validationContext.getGraphQLContext().put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)

        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector,
                { rule -> rule == OperationValidationRule.DEFER_DIRECTIVE_ON_ROOT_LEVEL }))
    }


    def "Not allow defer on subscription root level"() {
        given:
        def query = """
            subscription pets {
                ... @defer {
                    dog {
                        name
                    }
                }
            }
        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

    }

    def "Not allow defer mutation root level "() {
        given:
        def query = """
            mutation dog {
                ... @defer {
                    createDog(input: {id: "1"}) {
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
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root mutation type 'PetMutationType'"

    }

    def "Defer directive is allowed on query root level"() {
        given:
        def query = """
          query defer_query {
            ... @defer {
                dog {
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

    def "Not allow defer mutation root level on inline fragments "() {
        given:
        def query = """
           mutation doggo {
              ... {
                    ... @defer {
                        createDog(input: {id: "1"}) {
                            name
                        }
                    }

                }
            }
        """
        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root mutation type 'PetMutationType'"
    }

    def "Not allow defer on subscription root level even when is inside multiple inline fragment"() {
        given:
        def query = """
            subscription pets {
                ...{
                    ...{
                        ... @defer {
                            dog {
                                name
                            }
                        }
                   }
               }
            }
        """
        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root subscription type 'SubscriptionRoot'"

    }


    def "Not allow defer  on mutation root level even when ih multiple inline fragments split in fragment"() {
        given:
        def query = """
            fragment doggo on PetMutationType {
                ... {
                     ... @defer {
                        createDog(id: "1") {
                                id
                            }
                        }
                }
            }

            mutation doggoMutation {
                ...{
                    ...doggo
                }
           }


        """
        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[doggo]) : Defer directive cannot be used on root mutation type 'PetMutationType'"
    }


    def "Allows defer on mutation when it is not on root level"() {
        given:
        def query = """
            mutation pets {
                createDog(input: {id: "1"}) {
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

    def "allow defer on fragment when is not on mutation root level"() {
        given:
        def query = """
            mutation doggo {
                ...{
                    createDog(id: "1") {
                        ...doggo
                    }
                }
           }

            fragment doggo on Dog {
                ... @defer {
                    id
                }
            }

        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }


    def "allow defer on split fragment when is not on mutation root level"() {
        given:
        def query = """
            mutation doggo {
              ...doggoCreate
           }

            fragment doggoCreate on PetMutationType {
                  createDog(id: "1") {
                        ...doggoFields
                    }
            }

            fragment doggoFields on Dog {
                ... @defer {
                    id
                }
            }

        """
        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()

    }


    def "Not allow defer subscription root level even when there are multiple subscriptions"() {
        given:
        def query = """
            subscription pets {
                dog {
                    name
                }
            }
            subscription dog {
                ... @defer {
                    dog {
                         name
                    }
                }
            }

            subscription morePets {
                cat {
                    name
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.size() == 1

    }

    def "Not allow defer on mutation root level when there are multiple fragment levels regarless fragment order on query"() {
        given:
        def query = """

            fragment createDoggoRoot on PetMutationType {
                ... {
                    ...createDoggo
                }
            }

           mutation createDoggoRootOp {
              ...createDoggoRoot
            }

            fragment createDoggo on PetMutationType {
                ... {
                    ... @defer {
                        createDog(input: {id: "1"}) {
                            name
                        }
                    }
                }
            }

        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[createDoggoRoot/createDoggo]) : Defer directive cannot be used on root mutation type 'PetMutationType'"

    }

    def "Not allow defer on mutation root level even when there are multiple fragments and operations"() {
        given:
        def query = """

            fragment createDoggoLevel1 on PetMutationType {
                ... {
                    ... {
                        ...createDoggoLevel2
                    }
                }
            }

            fragment createDoggoLevel2 on PetMutationType {
                ...createDoggo
            }

            fragment createDoggo on PetMutationType {
                ... {
                    ... @defer {
                        createDog(input: {id: "1"}) {
                            name
                        }
                    }
                }
            }

            query pets1 {
                ... @defer {
                    dog {
                        name
                    }
                }
            }

           mutation createDoggo {
              ...createDoggoLevel1
           }

        """

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective@[createDoggoLevel1/createDoggoLevel2/createDoggo]) : Defer directive cannot be used on root mutation type 'PetMutationType'"

    }


    def "Not allow defer on subscription root level even when defer(if == false) "() {
        given:
        def query = """
            subscription pets{
                ... @defer(if:false) {
                    dog {

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
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root subscription type 'SubscriptionRoot'"

    }

    def "Not allow defer on subscription root level when defer(if == true) "() {
        given:
        def query = """
            subscription pets{
                ... @defer(if:true) {
                    dog {

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
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root subscription type 'SubscriptionRoot'"

    }

    def "Not allow defer on mutation root level even when if is variable that could have false as value "() {
        given:
        def query = """
            mutation pets(\$ifVar:Boolean){
                ... @defer(if:\$ifVar) {
                    createDog(input: {id: "1"}) {
                       name
                    }
                }

            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root mutation type 'PetMutationType'"
    }

    def "Not allow defer on mutation root level when defer(if == true) "() {
        given:
        def query = """
            mutation pets{
                ... @defer(if:true) {
                    createDog(input: {id: "1"}) {
                       name
                    }
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.size() == 1
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)
        errorCollector.errors.get(0).message == "Validation error (MisplacedDirective) : Defer directive cannot be used on root mutation type 'PetMutationType'"

    }

}
