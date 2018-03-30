package graphql.language

import graphql.AssertException
import spock.lang.Specification

class NodeParentTreeTest extends Specification {

    def strValue = new StringValue("123")
    def argument = new Argument("arg", strValue)
    def fieldDef = new FieldDefinition("field")
    def objectTypeDef = new ObjectTypeDefinition("object")

    def "basic hierarchy"() {
        given:
        Deque<Node> stack = dequeOf([argument, fieldDef, objectTypeDef])
        when:
        def info = new NodeParentTree(stack)
        then:
        info.getNode() instanceof Argument
        info.getParentInfo().isPresent()
        info.getParentInfo().get().getNode() instanceof FieldDefinition
        info.getParentInfo().get().getParentInfo().isPresent()
        info.getParentInfo().get().getParentInfo().get().getNode() instanceof ObjectTypeDefinition

    }

    def "non named nodes don't contribute to the path"() {
        given:
        Deque<Node> stack = dequeOf([strValue, argument, fieldDef, objectTypeDef])
        when:
        def info = new NodeParentTree(stack)
        then:
        info.getPath() == ["arg", "field", "object"]
    }

    def "single stack has no parent"() {
        given:
        Deque<Node> stack = dequeOf([argument])
        when:
        def info = new NodeParentTree(stack)
        then:
        info.getNode() instanceof Argument
        !info.getParentInfo().isPresent()
    }

    def "empty and null stack is not allowed"() {
        when:
        new NodeParentTree(new ArrayDeque<Node>())
        then:
        thrown(AssertException)
        when:
        new NodeParentTree(null)
        then:
        thrown(AssertException)
    }

    Deque<Node> dequeOf(List<Node> abstractNodes) {
        return new ArrayDeque<Node>(abstractNodes)
    }
}