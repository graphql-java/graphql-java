package graphql.validation

import graphql.TestUtil
import graphql.i18n.I18n
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class RulesVisitorTest extends Specification {

    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        I18n i18n = I18n.i18n(I18n.BundleType.Validation, Locale.ENGLISH)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document, i18n)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new OperationValidator(validationContext, errorCollector, { rule -> true }))
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
}
