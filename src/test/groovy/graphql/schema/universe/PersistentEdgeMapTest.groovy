package graphql.schema.universe

import spock.lang.Specification

class PersistentEdgeMapTest extends Specification {

    def "unique entry traversal skips structurally shared nodes"() {
        given:
        def firstEdges = edgesTo(101)
        def secondEdges = edgesTo(102)
        def thirdEdges = edgesTo(103)
        def base = PersistentEdgeMap.empty()
                .put(1, firstEdges)
                .put(2, secondEdges)
        def changed = base.put(3, thirdEdges)
        def visitedNodes = Collections.newSetFromMap(
                new IdentityHashMap<IntMapNode<PackedEdgeSet>, Boolean>())
        def visitedEntries = []

        when:
        PersistentEdgeMap.empty().visitUniqueEntries(
                visitedNodes,
                (sourceId, edges) -> visitedEntries.add([sourceId, edges]))
        base.visitUniqueEntries(
                visitedNodes,
                (sourceId, edges) -> visitedEntries.add([sourceId, edges]))
        changed.visitUniqueEntries(
                visitedNodes,
                (sourceId, edges) -> visitedEntries.add([sourceId, edges]))
        changed.visitUniqueEntries(
                visitedNodes,
                (sourceId, edges) -> visitedEntries.add([sourceId, edges]))

        then:
        visitedEntries.size() == 3
        visitedEntries.collect { it[0] }.toSet() == [1, 2, 3] as Set
        visitedEntries.find { it[0] == 1 }[1].is(firstEdges)
        visitedEntries.find { it[0] == 2 }[1].is(secondEdges)
        visitedEntries.find { it[0] == 3 }[1].is(thirdEdges)
    }

    private static PackedEdgeSet edgesTo(int targetId) {
        new PackedEdgeSet([
                PackedEdgeSet.pack(SUEdgeKind.TYPE, 1, targetId)
        ] as long[])
    }
}
