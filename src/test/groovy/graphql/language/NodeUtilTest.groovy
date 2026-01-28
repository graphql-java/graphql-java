package graphql.language


import graphql.TestUtil
import graphql.execution.UnknownOperationException
import spock.lang.Specification

class NodeUtilTest extends Specification {

    def "getOperation: when multiple operations are defined in the query and operation name is missing then it should throw UnknownOperationException"() {
        setup:
        def doc = TestUtil.parseQuery('''
            query Q1 { id }
            query Q2 { id }
        ''')

        when:
        NodeUtil.getOperation(doc, operationName)

        then:
        def ex = thrown(UnknownOperationException)
        ex.message == "Must provide operation name if query contains multiple operations."

        where:
        operationName << [null, '']
    }

    def "getOperation: when multiple operations are defined in the query and operation name doesn't match any of the query operations then it should throw UnknownOperationException"() {
        setup:
        def doc = TestUtil.parseQuery('''
            query Q1 { id }
            query Q2 { id }
        ''')

        when:
        NodeUtil.getOperation(doc, 'Unknown')

        then:
        def ex = thrown(UnknownOperationException)
        ex.message == "Unknown operation named 'Unknown'."
    }

    def d1 = Directive.newDirective().name("d1").build()
    def d2 = Directive.newDirective().name("d2").build()
    def d3r = Directive.newDirective().name("d3").build()

      def "can create a map of all directives"() {
        when:
        def result = NodeUtil.allDirectivesByName([d1, d2, d3r, d3r])
        then:
        result == [d1: [d1], d2: [d2], d3: [d3r, d3r],]
    }


}
