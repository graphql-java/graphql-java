package graphql.util

import graphql.AssertException
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

    def "context variables preserve live inheritance and sibling isolation"() {
        given:
        def rootVars = [(String): "root value"]
        def root = state.newRootContext(rootVars)
        state.addNewContexts(["parent", "sibling"], root)
        def parent = state.pop() as TraverserContext
        def sibling = state.pop() as TraverserContext
        state.addNewContexts(["child"], parent)
        def child = state.pop() as TraverserContext

        expect:
        parent.getVar(String) == null
        child.getVar(String) == null
        child.getVarFromParents(String) == "root value"
        child.getVarFromParents(Integer) == null

        when: "a parent acquires variables after its children were created"
        parent.setVar(String, "parent value")
        child.setVar(String, "child value")
        sibling.setVar(Integer, 42)

        then:
        parent.getVar(String) == "parent value"
        child.getVar(String) == "child value"
        child.getVarFromParents(String) == "parent value"
        sibling.getVar(String) == null
        sibling.getVarFromParents(String) == "root value"
        sibling.getVar(Integer) == 42
        parent.getVar(Integer) == null
        child.getVarFromParents(Integer) == null
        rootVars == [(String): "root value"]

        when: "null values fall through to an updated ancestor"
        parent.setVar(String, null)
        rootVars[String] = "updated root value"

        then:
        parent.getVar(String) == null
        child.getVar(String) == "child value"
        child.getVarFromParents(String) == "updated root value"
        sibling.getVarFromParents(String) == "updated root value"

        where:
        state << [TraverserState.newStackState(null), TraverserState.newQueueState(null)]
    }

    def "context variables retain a supplied map"() {
        given:
        def vars = [:]
        def context = new DefaultTraverserContext<>("node", null, [] as Set, vars, null, null, false, false)

        when:
        vars[String] = "external value"

        then:
        context.getVar(String) == "external value"

        when:
        context.setVar(Integer, 42)

        then:
        vars == [(String): "external value", (Integer): 42]
    }

    def "root context requires a variable map"() {
        when:
        state.newRootContext(null)

        then:
        thrown(AssertException)

        where:
        state << [TraverserState.newStackState(null), TraverserState.newQueueState(null)]
    }

    private static <T> DefaultTraverserContext<T> child(T node, TraverserContext<T> parent, NodeLocation location) {
        new DefaultTraverserContext<T>(node, parent, [] as Set, [:], null, location, false, false)
    }
}
