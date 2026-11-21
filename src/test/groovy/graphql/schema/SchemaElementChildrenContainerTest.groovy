package graphql.schema

import spock.lang.Specification

import static graphql.schema.GraphQLTypeReference.typeRef
import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer

class SchemaElementChildrenContainerTest extends Specification {

    def "flat children preserve group order and duplicate references independently of the container"() {
        given:
        def first = typeRef("First")
        def second = typeRef("Second")
        def container = newSchemaElementChildrenContainer()
                .children("first group", [first, second])
                .children("empty group", [])
                .child("last group", first)
                .build()

        when:
        def flattened = container.childrenAsList

        then:
        flattened == [first, second, first]

        when:
        flattened.clear()
        flattened.add(second)

        then:
        flattened == [second]
        container.childrenAsList == [first, second, first]
        container.getChildren("first group") == [first, second]
        container.getChildren("empty group").empty
    }

    def "an empty container returns an independently mutable flat list"() {
        given:
        def container = newSchemaElementChildrenContainer().build()
        def flattened = container.childrenAsList
        def child = typeRef("Child")

        when:
        flattened.add(child)

        then:
        flattened == [child]
        container.childrenAsList.empty
        container.empty
    }

    def "flat children snapshot supplied lists without requiring random access"() {
        given:
        def first = typeRef("First")
        def second = typeRef("Second")
        def children = new LinkedList<GraphQLSchemaElement>([first])
        def container = newSchemaElementChildrenContainer([types: children]).build()
        def before = container.childrenAsList

        when:
        children.add(second)

        then:
        before == [first]
        container.childrenAsList == [first, second]
    }
}
