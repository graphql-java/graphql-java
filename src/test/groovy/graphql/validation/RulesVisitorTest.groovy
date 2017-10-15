package graphql.validation

import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import graphql.validation.rules.NoUnusedVariables
import spock.lang.Specification

class RulesVisitorTest extends Specification {
    ValidationErrorCollector errorCollector = new ValidationErrorCollector()

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        // this is one of the rules which checks inside fragment spreads, so it's needed to test this
        NoUnusedVariables noUnusedVariables = new NoUnusedVariables(validationContext, errorCollector)

        languageTraversal.traverse(document, new RulesVisitor(validationContext, [noUnusedVariables]))
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
