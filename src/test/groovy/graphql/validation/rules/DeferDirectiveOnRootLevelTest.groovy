package graphql.validation.rules

import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.SpecValidationSchema
import graphql.validation.TraversalContext
import graphql.validation.ValidationContext
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import graphql.validation.Validator
import spock.lang.Specification

class DeferDirectiveOnRootLevelTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    DeferDirectiveOnRootLevel deferDirectiveOnRootLevel = new DeferDirectiveOnRootLevel(validationContext, errorCollector)

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(SpecValidationSchema.specValidationSchema, document, i18n)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [new DeferDirectiveOnRootLevel(validationContext, errorCollector)]))
    }

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> SpecValidationSchema.specValidationSchema
        validationContext.getTraversalContext() >> traversalContext
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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective) : Directive 'defer' is not allowed on root of operation 'mutation'"

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
        def validationErrors = validate(query)

        then:
        validationErrors.isEmpty()
    }

    def "allow defer on  when is not on mutation root level"() {
        given:
        def query = """
            mutation doggo {
                createDog(id: "1") {
                    ... @defer {
                        id
                    }
                }
           }
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective) : Directive 'defer' is not allowed on root of operation 'mutation'"

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        traverse(query)

        then:
        !errorCollector.errors.isEmpty()
        errorCollector.containsValidationError(ValidationErrorType.MisplacedDirective)

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

        then:
        errorCollector.errors.isEmpty()
    }

    def "allow defer on fragment when is not on mutation root level"() {
        given:
        def query = """
            mutation doggo {
                ...{
                  ...doggoCreate
                }
           }

            fragment doggoCreate on PetMutationType {
                  createDog(id: "1") {
                        ... @defer {
                            id
                        }
                    }
            }
            
        """
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

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
        Document document = new Parser().parseDocument(query)
        LanguageTraversal languageTraversal = new LanguageTraversal()

        when:
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [deferDirectiveOnRootLevel]))

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
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective@[createDoggoRoot/createDoggo]) : Directive 'defer' is not allowed on root of operation 'mutation'"

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
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective@[createDoggoLevel1/createDoggoLevel2/createDoggo]) : Directive 'defer' is not allowed on root of operation 'mutation'"

    }

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}

