package graphql.validation

import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

class RulesVisitorTest extends Specification {
    AbstractRule simpleRule = Mock()
    AbstractRule visitsSpreadsRule = Mock()

    def setup() {
        visitsSpreadsRule.isVisitFragmentSpreads() >> true
    }

    def traverse(String query) {
        Document document = new Parser().parseDocument(query)
        ValidationContext validationContext = new ValidationContext(TestUtil.dummySchema, document)
        LanguageTraversal languageTraversal = new LanguageTraversal()
        languageTraversal.traverse(document, new RulesVisitor(validationContext, [simpleRule, visitsSpreadsRule]))
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

    def "RulesVisitor visits fragment definition with isVisitFragmentSpread rules once per operation"() {
        given:
        def query = """
        fragment A on A { __typename }
        fragment B on B { ...A }
        fragment C on C { ...A ...B }
        
        query Q1 { ...A ...B ...C }
        query Q2 { ...A ...B ...C }
        """

        when:
        traverse(query)

        then:
        2 * visitsSpreadsRule.checkFragmentDefinition({it.name == "A"})
        2 * visitsSpreadsRule.checkFragmentDefinition({it.name == "B"})
        2 * visitsSpreadsRule.checkFragmentDefinition({it.name == "C"})
    }
}

