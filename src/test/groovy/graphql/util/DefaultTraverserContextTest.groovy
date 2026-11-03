package graphql.util

import spock.lang.Specification

class DefaultTraverserContextTest extends Specification {

    def "root contexts have no breadcrumbs"() {
        expect:
        DefaultTraverserContext.dummy().breadcrumbs.empty
        DefaultTraverserContext.simple("root").breadcrumbs.empty
        child("root", DefaultTraverserContext.dummy(), null).breadcrumbs.empty
    }

    def "breadcrumbs preserve the parent snapshots and locations when nodes change"() {
        given:
        def root = child("root", DefaultTraverserContext.dummy(), null)
        def firstLocation = new NodeLocation("fields", 2)
        def secondLocation = new NodeLocation("arguments", 1)
        def parent = child("parent", root, firstLocation)
        def leaf = child("leaf", parent, secondLocation)

        when:
        root.deleteNode()
        parent.changeNode("changed parent")

        then:
        leaf.breadcrumbs == [new Breadcrumb("parent", secondLocation), new Breadcrumb("root", firstLocation)]
        parent.breadcrumbs == [new Breadcrumb("root", firstLocation)]
        leaf.breadcrumbs == [new Breadcrumb("parent", secondLocation), new Breadcrumb("root", firstLocation)]
        child("new leaf", parent, secondLocation).breadcrumbs == [
                new Breadcrumb("changed parent", secondLocation), new Breadcrumb("root", firstLocation)]

        when:
        leaf.breadcrumbs.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "breadcrumbs snapshot custom parent contexts"() {
        given:
        def ancestorLocation = new NodeLocation("ancestor", 0)
        def leafLocation = new NodeLocation("leaf", 3)
        def ancestors = [new Breadcrumb("ancestor", ancestorLocation)]
        def parent = Stub(parentType) {
            isRootContext() >> false
            thisNode() >> "custom parent"
            getBreadcrumbs() >> ancestors
        }
        def leaf = child("leaf", parent, leafLocation)

        when:
        ancestors.clear()

        then:
        leaf.breadcrumbs == [new Breadcrumb("custom parent", leafLocation), new Breadcrumb("ancestor", ancestorLocation)]

        where:
        parentType << [TraverserContext, DefaultTraverserContext]
    }

    def "breadcrumbs can be requested after a deep traversal without recursive calls"() {
        given:
        def context = child(0, DefaultTraverserContext.dummy(), null)
        def location = new NodeLocation("child", 0)
        int depth = 20_000
        for (int number = 1; number <= depth; number++) {
            context = child(number, context, location)
        }

        when:
        def breadcrumbs = context.breadcrumbs

        then:
        breadcrumbs.size() == depth
        breadcrumbs*.node == (depth - 1..0).toList()
        breadcrumbs.every { it.location == location }
    }

    private static <T> DefaultTraverserContext<T> child(T node, TraverserContext<T> parent, NodeLocation location) {
        new DefaultTraverserContext<T>(node, parent, [] as Set, [:], null, location, false, false)
    }
}
