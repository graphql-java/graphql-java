package graphql.relay

import com.google.common.collect.ImmutableList
import spock.lang.Specification

class DefaultConnectionTest extends Specification {

    def "test equality and hashcode"() {
        def edges1 = ImmutableList.of(new DefaultEdge("a", new DefaultConnectionCursor("a")))
        def edges2 = ImmutableList.of(new DefaultEdge("b", new DefaultConnectionCursor("b")))

        def pageInfo1 = new DefaultPageInfo(new DefaultConnectionCursor("c"), new DefaultConnectionCursor("c"), true, false)
        def pageInfo2 = new DefaultPageInfo(new DefaultConnectionCursor("d"), new DefaultConnectionCursor("d"), false, true)

        expect:

        assert new DefaultConnection(edges1, pageInfo1).equals(new DefaultConnection(edges1, pageInfo1))
        assert !new DefaultConnection(edges1, pageInfo2).equals(new DefaultConnection(edges1, pageInfo1))
        assert !new DefaultConnection(edges1, pageInfo1).equals(new DefaultConnection(edges2, pageInfo1))
        assert !new DefaultConnection(edges1, pageInfo1).equals(new DefaultConnection(edges1, pageInfo2))

        assert new DefaultConnection(edges1, pageInfo1).hashCode() == new DefaultConnection(edges1, pageInfo1).hashCode()
        assert new DefaultConnection(edges1, pageInfo2).hashCode() != new DefaultConnection(edges1, pageInfo1).hashCode()
        assert new DefaultConnection(edges1, pageInfo1).hashCode() != new DefaultConnection(edges2, pageInfo1).hashCode()
        assert new DefaultConnection(edges1, pageInfo1).hashCode() != new DefaultConnection(edges1, pageInfo2).hashCode()
    }
}
