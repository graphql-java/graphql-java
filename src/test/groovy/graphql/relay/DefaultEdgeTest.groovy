package graphql.relay

import spock.lang.Specification

class DefaultEdgeTest extends Specification {

    def "test equality and hashcode"() {
        expect:

        assert new DefaultEdge(leftNode, new DefaultConnectionCursor(leftConnectionCursor)).equals(
                new DefaultEdge(rightNode, new DefaultConnectionCursor(rightConnectionCursor))) == isEqual

        assert (new DefaultEdge(leftNode, new DefaultConnectionCursor(leftConnectionCursor)).hashCode() ==
                new DefaultEdge(rightNode, new DefaultConnectionCursor(rightConnectionCursor)).hashCode()) == isEqual

        where:

        leftNode | leftConnectionCursor | rightNode | rightConnectionCursor || isEqual
        "a"      | "b"                  | "a"       | "b"                   || true
        "x"      | "b"                  | "a"       | "b"                   || false
        "a"      | "x"                  | "a"       | "b"                   || false
        "a"      | "b"                  | "x"       | "b"                   || false
        "a"      | "b"                  | "a"       | "x"                   || false
    }
}
