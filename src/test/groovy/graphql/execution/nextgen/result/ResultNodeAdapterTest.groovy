package graphql.execution.nextgen.result

import graphql.AssertException
import graphql.util.NodeLocation
import spock.lang.Specification

import static graphql.execution.nextgen.result.ExecutionResultNodeTestUtils.esi
import static graphql.execution.nextgen.result.ExecutionResultNodeTestUtils.resolvedValue

class ResultNodeAdapterTest extends Specification {

    def parentNode = new ObjectExecutionResultNode(null, null, [
            new LeafExecutionResultNode(esi(), resolvedValue("v1"), null),
            new LeafExecutionResultNode(esi(), resolvedValue("v2"), null),
            new LeafExecutionResultNode(esi(), resolvedValue("v3"), null),
    ])

    def "can remove a child from a node"() {
        when:
        ResultNodeAdapter.RESULT_NODE_ADAPTER.removeChild(parentNode, new NodeLocation(null, -1))
        then:
        thrown(AssertException)

        when:
        ResultNodeAdapter.RESULT_NODE_ADAPTER.removeChild(parentNode, new NodeLocation(null, -3))
        then:
        thrown(AssertException)

        when:
        def newNode = ResultNodeAdapter.RESULT_NODE_ADAPTER.removeChild(parentNode, new NodeLocation(null, 1))
        then:
        newNode.children.size() == 2
        newNode.children[0].getResolvedValue().getCompletedValue() == "v1"
        newNode.children[1].getResolvedValue().getCompletedValue() == "v3"
    }
}
