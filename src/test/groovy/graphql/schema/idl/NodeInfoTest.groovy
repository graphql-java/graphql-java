package graphql.schema.idl

import graphql.AssertException
import graphql.language.Argument
import graphql.language.FieldDefinition
import graphql.language.Node
import graphql.language.ObjectTypeDefinition
import graphql.language.StringValue
import spock.lang.Specification

class NodeInfoTest extends Specification {

    def argument = new Argument("arg", new StringValue("123"))
    def fieldDef = new FieldDefinition("field")
    def objectTypeDef = new ObjectTypeDefinition("object")

    def "basic hierarchy"() {
        given:
        Deque<Node> stack = dequeOf([argument, fieldDef, objectTypeDef])
        when:
        def info = new NodeInfo(stack)
        then:
        info.getNode() instanceof Argument
        info.getParentInfo().isPresent()
        info.getParentInfo().get().getNode() instanceof FieldDefinition
        info.getParentInfo().get().getParentInfo().isPresent()
        info.getParentInfo().get().getParentInfo().get().getNode() instanceof ObjectTypeDefinition
    }

    def "single stack has no parent"() {
        given:
        Deque<Node> stack = dequeOf([argument])
        when:
        def info = new NodeInfo(stack)
        then:
        info.getNode() instanceof Argument
        !info.getParentInfo().isPresent()
    }

    def "empty and null stack is not allowed"() {
        when:
        new NodeInfo(new ArrayDeque<Node>())
        then:
        thrown(AssertException)
        when:
        new NodeInfo(null)
        then:
        thrown(AssertException)
    }

    Deque<Node> dequeOf(List<Node> abstractNodes) {
        return new ArrayDeque<Node>(abstractNodes)
    }
}
