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

class DeferDirectiveOnValidOperationTest extends Specification {

    ValidationContext validationContext = Mock(ValidationContext)
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(SpecValidationSchema.specValidationSchema, document, i18n)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [new DeferDirectiveOnValidOperation(validationContext, errorCollector)]))
    }

    def setup() {
        def traversalContext = Mock(TraversalContext)
        validationContext.getSchema() >> SpecValidationSchema.specValidationSchema
        validationContext.getTraversalContext() >> traversalContext
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
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective@[doggoSubscription/dog/doggo]) : Directive 'defer' is not allowed on operation 'subscription'"

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
        def validationErrors = validate(query)

        then:
        !validationErrors.isEmpty()
        validationErrors.size() == 1
        validationErrors.get(0).getValidationErrorType() == ValidationErrorType.MisplacedDirective
        validationErrors.get(0).message == "Validation error (MisplacedDirective@[dog]) : Directive 'defer' is not allowed on operation 'subscription'"

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

    static List<ValidationError> validate(String query) {
        def document = new Parser().parseDocument(query)
        return new Validator().validateDocument(SpecValidationSchema.specValidationSchema, document, Locale.ENGLISH)
    }
}

