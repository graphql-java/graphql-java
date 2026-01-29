package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import java.util.function.Predicate

class RulesVisitorTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        traverse(query, TestUtil.dummySchema, { rule -> true })
    }

    def traverse(String query, GraphQLSchema schema, Predicate<OperationValidationRule> rulePredicate) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(schema, document, i18n)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector, rulePredicate))
    }

    def "RulesVisitor does not repeatedly spread directly recursive fragments leading to a stackoverflow"() {
        given:
        def query = """
        query directFragmentRecursion {
            __schema {
                queryType {
                    ...Recursive
                }
            }
        }

        fragment Recursive on __Type {
            ofType {
              ...Recursive
             }
        }
        """
        when:
        traverse(query)
        then:
        notThrown(StackOverflowError)
    }

    def "RulesVisitor does not repeatedly spread indirectly recursive fragments leading to a stackoverflow"() {
        given:
        def query = """
        query directFragmentRecursion {
            __schema {
                queryType {
                    ...CycleA
                }
            }
        }

        fragment CycleA on __Type {
            ofType {
                ...CycleB
            }
        }

        fragment CycleB on __Type {
            ofType{
                ...CycleA
            }
        }
        """
        when:
        traverse(query)
        then:
        notThrown(StackOverflowError)
    }

    def "OperationValidator visits fragment definitions per-operation for fragment-spread rules"() {
        given:
        def query = """
        fragment HumanFields on __Type { fields(includeDeprecated: \$inc) { name } }

        query Q1(\$inc: Boolean!) { __schema { queryType { ...HumanFields } } }
        query Q2 { __schema { queryType { ...HumanFields } } }
        """
        when:
        traverse(query, TestUtil.dummySchema, { r -> r == OperationValidationRule.NO_UNDEFINED_VARIABLES })
        then:
        // Q2 has undefined variable $inc -> exactly 1 error
        errorCollector.errors.size() == 1
        errorCollector.errors[0].validationErrorType == ValidationErrorType.UndefinedVariable
    }
}
