package graphql.schema.universe

import graphql.AssertException
import spock.lang.Specification

class SUSchemaPossibleTypesTest extends Specification {

    def "getPossibleTypes supports every composite type"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def emptyInterface = universe.newInterfaceType("EmptyInterface")
        def search = universe.newUnionType("Search")
        def emptyUnion = universe.newUnionType("EmptyUnion")
        def alpha = universe.newObjectType("Alpha")
        def beta = universe.newObjectType("Beta")
        def other = universe.newObjectType("Other")
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addType(emptyInterface)
                .addType(emptyUnion)
                .addType(other)
                .addInterface(beta, node)
                .addInterface(alpha, node)
                .addUnionMember(search, alpha)
                .addUnionMember(search, beta)
                .build()

        expect:
        schema.getPossibleTypes(query) == [query]
        schema.getPossibleTypes(alpha) == [alpha]
        schema.getPossibleTypes(node) == [alpha, beta]
        schema.getPossibleTypes(emptyInterface).isEmpty()
        schema.getPossibleTypes(search) == [alpha, beta]
        schema.getPossibleTypes(emptyUnion).isEmpty()
    }

    def "isPossibleType supports every composite and object combination"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def emptyInterface = universe.newInterfaceType("EmptyInterface")
        def search = universe.newUnionType("Search")
        def emptyUnion = universe.newUnionType("EmptyUnion")
        def alpha = universe.newObjectType("Alpha")
        def beta = universe.newObjectType("Beta")
        def other = universe.newObjectType("Other")
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addType(emptyInterface)
                .addType(emptyUnion)
                .addType(other)
                .addInterface(alpha, node)
                .addInterface(beta, node)
                .addUnionMember(search, alpha)
                .addUnionMember(search, beta)
                .build()

        expect:
        schema.isPossibleType(query, query)
        !schema.isPossibleType(query, alpha)
        schema.isPossibleType(alpha, alpha)
        !schema.isPossibleType(alpha, query)

        schema.isPossibleType(node, alpha)
        schema.isPossibleType(node, beta)
        !schema.isPossibleType(node, query)
        !schema.isPossibleType(node, other)
        !schema.isPossibleType(emptyInterface, alpha)

        schema.isPossibleType(search, alpha)
        schema.isPossibleType(search, beta)
        !schema.isPossibleType(search, query)
        !schema.isPossibleType(search, other)
        !schema.isPossibleType(emptyUnion, alpha)
    }

    def "possible type lookups follow their schema snapshot"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def search = universe.newUnionType("Search")
        def alpha = universe.newObjectType("Alpha")
        def beta = universe.newObjectType("Beta")
        def base = universe.newSchema("base")
                .queryType(query)
                .addInterface(alpha, node)
                .addUnionMember(search, alpha)
                .addType(beta)
                .build()

        when:
        def changed = base.transform("changed", builder -> builder
                .removeInterface(alpha, node)
                .addInterface(beta, node)
                .removeUnionMember(search, alpha)
                .addUnionMember(search, beta))

        then:
        base.getPossibleTypes(node) == [alpha]
        base.getPossibleTypes(search) == [alpha]
        base.isPossibleType(node, alpha)
        base.isPossibleType(search, alpha)
        !base.isPossibleType(node, beta)
        !base.isPossibleType(search, beta)

        changed.getPossibleTypes(node) == [beta]
        changed.getPossibleTypes(search) == [beta]
        !changed.isPossibleType(node, alpha)
        !changed.isPossibleType(search, alpha)
        changed.isPossibleType(node, beta)
        changed.isPossibleType(search, beta)
    }

    def "possible type lists are immutable for every composite type"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def search = universe.newUnionType("Search")
        def object = universe.newObjectType("Object")
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addInterface(object, node)
                .addUnionMember(search, object)
                .build()

        when:
        schema.getPossibleTypes(query).add(object)

        then:
        thrown(UnsupportedOperationException)

        when:
        schema.getPossibleTypes(node).add(query)

        then:
        thrown(UnsupportedOperationException)

        when:
        schema.getPossibleTypes(search).add(query)

        then:
        thrown(UnsupportedOperationException)
    }

    def "possible type lookups reject vertices from another universe"() {
        given:
        def first = new SchemaUniverse()
        def second = new SchemaUniverse()
        def query = first.newObjectType("Query")
        def node = first.newInterfaceType("Node")
        def foreignInterface = second.newInterfaceType("Foreign")
        def foreignObject = second.newObjectType("ForeignObject")
        def schema = first.newSchema("schema")
                .queryType(query)
                .addType(node)
                .build()

        when:
        schema.getPossibleTypes(foreignInterface)

        then:
        thrown(AssertException)

        when:
        schema.isPossibleType(foreignInterface, query)

        then:
        thrown(AssertException)

        when:
        schema.isPossibleType(node, foreignObject)

        then:
        thrown(AssertException)
    }
}
