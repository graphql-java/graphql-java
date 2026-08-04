package graphql.schema.universe

import spock.lang.Specification

class PersistentIntMapTest extends Specification {

    def "persistent updates preserve values and identity no-ops"() {
        given:
        def first = new Object()
        def second = new Object()
        def replacement = new Object()
        def empty = PersistentIntMap.empty()

        when:
        def base = empty.put(1, first).put(2, second)
        def changed = base.put(1, replacement)
        def removed = changed.remove(2)

        then:
        empty.get(1) == null
        empty.remove(1).is(empty)
        base.get(1).is(first)
        base.get(2).is(second)
        base.put(1, first).is(base)
        changed.get(1).is(replacement)
        changed.get(2).is(second)
        removed.get(1).is(replacement)
        removed.get(2) == null
        removed.remove(2).is(removed)
        base.remove(999).is(base)
    }

    def "removal handles keys sharing an initial trie slot"() {
        given:
        def keys = (0..128)
                .groupBy { PersistentIntMap.mix(it) & 31 }
                .values()
                .find { it.size() > 2 }
                .take(3)
        def map = PersistentIntMap.empty()
                .put(keys[0], "first")
                .put(keys[1], "second")

        when:
        def unchanged = map.remove(keys[2])
        def one = map.remove(keys[0])
        def empty = one.remove(keys[1])

        then:
        unchanged.is(map)
        one.get(keys[0]) == null
        one.get(keys[1]) == "second"
        empty.get(keys[1]) == null
    }

    def "unique traversal skips structurally shared nodes"() {
        given:
        def base = PersistentIntMap.empty()
                .put(1, "first")
                .put(2, "second")
        def changed = base.put(3, "third")
        def visitedNodes = Collections.newSetFromMap(
                new IdentityHashMap<IntMapNode<String>, Boolean>())
        def entries = []

        when:
        PersistentIntMap.empty().visitUniqueEntries(
                visitedNodes,
                (key, value) -> entries.add([key, value]))
        base.visitUniqueEntries(
                visitedNodes,
                (key, value) -> entries.add([key, value]))
        changed.visitUniqueEntries(
                visitedNodes,
                (key, value) -> entries.add([key, value]))
        changed.visitUniqueEntries(
                visitedNodes,
                (key, value) -> entries.add([key, value]))

        then:
        entries.toSet() == [
                [1, "first"],
                [2, "second"],
                [3, "third"]
        ] as Set
    }
}
