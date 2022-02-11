package graphql.validation.rules


import graphql.StarWarsSchema
import graphql.parser.Parser
import graphql.validation.LanguageTraversal
import graphql.validation.RulesVisitor
import graphql.validation.ValidationContext
import graphql.validation.ValidationErrorCollector
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class VariableTypesMatchRuleTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        def document = Parser.parse(query)
        def validationContext = new ValidationContext(StarWarsSchema.starWarsSchema, document)
        def variableTypesMatchRule = new VariableTypesMatchRule(validationContext, errorCollector)
        def languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [variableTypesMatchRule]))
    }

    def "valid variables"() {
        given:
        def query = """
            query Q(\$id: String!) {
                human(id: \$id) { 
                    __typename 
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.errors.isEmpty()
    }

    def "invalid variables"() {
        given:
        def query = """
            query Q(\$id: String) {
                human(id: \$id) { 
                    __typename 
                }
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        // #991: describe which types were mismatched in error message
        errorCollector.errors[0].message.contains("Variable type 'String' doesn't match expected type 'String!'")
    }

    def "invalid variables in fragment spread"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$xid) {
                  __typename
                }
            }
            
            query Invalid(\$xid: String) {
                ...QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message.contains("Variable type 'String' doesn't match expected type 'String!'")
    }

    def "mixed validity operations, valid first"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }
            
            query Valid(\$id: String!) {
                ... QueryType
            }
            
            query Invalid(\$id: String) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message.contains("Variable type 'String' doesn't match expected type 'String!'")
    }

    def "mixed validity operations, invalid first"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }
            
            query Invalid(\$id: String) {
                ... QueryType
            }
            
            query Valid(\$id: String!) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.containsValidationError(ValidationErrorType.VariableTypeMismatch)
        errorCollector.errors[0].message.contains("Variable type 'String' doesn't match expected type 'String!'")
    }

    def "multiple invalid operations"() {
        given:
        def query = """
            fragment QueryType on QueryType {
                human(id: \$id) {
                  __typename
                }
            }
            
            query Invalid1(\$id: String) {
                ... QueryType
            }
            
            query Invalid2(\$id: Boolean) {
                ... QueryType
            }
        """

        when:
        traverse(query)

        then:
        errorCollector.getErrors().size() == 2
        errorCollector.errors.any {
            it.validationErrorType == ValidationErrorType.VariableTypeMismatch &&
                it.message.contains("Variable type 'String' doesn't match expected type 'String!'")
        }
        errorCollector.errors.any {
            it.validationErrorType == ValidationErrorType.VariableTypeMismatch &&
                    it.message.contains("Variable type 'Boolean' doesn't match expected type 'String!'")
        }
    }
}
