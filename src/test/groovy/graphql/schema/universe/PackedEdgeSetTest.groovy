package graphql.schema.universe

import spock.lang.Specification

class PackedEdgeSetTest extends Specification {

    def "ordered ranges retain encounter order while other ranges remain sorted"() {
        given:
        def fieldB = PackedEdgeSet.pack(SUEdgeKind.FIELD, 20, 2)
        def fieldA = PackedEdgeSet.pack(SUEdgeKind.FIELD, 10, 1)
        def firstZ = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 30, 30)
        def a = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 10, 10)
        def secondZ = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 30, 31)
        def m = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 20, 20)
        def wrappedType = PackedEdgeSet.pack(SUEdgeKind.WRAPPED_TYPE, 0, 99)
        def mutable = new MutablePackedEdgeSet(PackedEdgeSet.empty())

        mutable.add(wrappedType)
        mutable.add(firstZ)
        mutable.add(fieldB)
        mutable.add(a)
        mutable.add(secondZ)
        mutable.add(firstZ)
        mutable.add(m)
        mutable.add(fieldA)

        when:
        def frozen = mutable.freeze()

        then:
        targets(frozen, SUEdgeKind.FIELD) == [1, 2]
        targets(frozen, SUEdgeKind.APPLIED_DIRECTIVE) == [30, 10, 31, 20]
        targets(frozen, SUEdgeKind.WRAPPED_TYPE) == [99]
        frozen.size() == 7

        and:
        frozen.contains(SUEdgeKind.FIELD, 10, 1)
        frozen.contains(SUEdgeKind.APPLIED_DIRECTIVE, 30, 30)
        frozen.contains(SUEdgeKind.APPLIED_DIRECTIVE, 30, 31)
        frozen.contains(SUEdgeKind.WRAPPED_TYPE, 0, 99)
        !frozen.contains(SUEdgeKind.APPLIED_DIRECTIVE, 30, 32)

        and:
        frozen.targetByName(SUEdgeKind.FIELD, 20) == 2
        frozen.targetByName(SUEdgeKind.APPLIED_DIRECTIVE, 30) == 30
        frozen.targetByName(SUEdgeKind.WRAPPED_TYPE, 0) == 99
        frozen.targetByName(SUEdgeKind.APPLIED_DIRECTIVE, 40) == -1

        and:
        frozen.endIndex(SUEdgeKind.FIELD) ==
                frozen.firstIndex(SUEdgeKind.APPLIED_DIRECTIVE)
        frozen.endIndex(SUEdgeKind.APPLIED_DIRECTIVE) ==
                frozen.firstIndex(SUEdgeKind.WRAPPED_TYPE)
    }

    def "replacing an ordered edge retains its array position"() {
        given:
        def first = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 30, 30)
        def current = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 10, 10)
        def replacement = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 5, 5)
        def last = PackedEdgeSet.pack(SUEdgeKind.APPLIED_DIRECTIVE, 20, 20)
        def mutable = new MutablePackedEdgeSet(PackedEdgeSet.empty())
        mutable.add(first)
        mutable.add(current)
        mutable.add(last)

        when:
        def replaced = mutable.replace(current, replacement)
        def frozen = mutable.freeze()

        then:
        replaced
        targets(frozen, SUEdgeKind.APPLIED_DIRECTIVE) == [30, 5, 20]

        when:
        def missing = mutable.replace(current, first)

        then:
        !missing
    }

    private static List<Integer> targets(PackedEdgeSet edges, SUEdgeKind kind) {
        def result = []
        for (int i = edges.firstIndex(kind); i < edges.endIndex(kind); i++) {
            result.add(edges.targetIdAt(i))
        }
        return result
    }
}
