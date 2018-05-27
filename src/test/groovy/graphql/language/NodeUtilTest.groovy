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
        NodeUtil.getOperation(doc, null)

        then:
        def ex = thrown(UnknownOperationException)
        ex.message == "Must provide operation name if query contains multiple operations."
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
}
