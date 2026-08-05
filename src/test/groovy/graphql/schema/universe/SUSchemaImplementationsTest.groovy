package graphql.schema.universe

import graphql.AssertException
import graphql.TestUtil
import spock.lang.Specification

class SUSchemaImplementationsTest extends Specification {

    def "implementations are indexed eagerly in name order"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def resource = universe.newInterfaceType("Resource")
        def empty = universe.newInterfaceType("Empty")
        def zebra = universe.newObjectType("Zebra")
        def alpha = universe.newObjectType("Alpha")
        def middle = universe.newObjectType("Middle")

        when:
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addType(empty)
                .addInterface(resource, node)
                .addInterface(zebra, node)
                .addInterface(alpha, resource)
                .addInterface(alpha, node)
                .addInterface(middle, resource)
                .build()

        then:
        schema.getImplementations(node) == [alpha, zebra]
        schema.getImplementations(resource) == [alpha, middle]
        schema.getImplementations(empty).isEmpty()
        !schema.getImplementations(node).contains(resource)

        when:
        schema.getImplementations(node).add(query)

        then:
        thrown(UnsupportedOperationException)
    }

    def "interface edits update only affected implementation buckets"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def resource = universe.newInterfaceType("Resource")
        def first = universe.newObjectType("First")
        def second = universe.newObjectType("Second")
        def third = universe.newObjectType("Third")
        def base = universe.newSchema("base")
                .queryType(query)
                .addInterface(first, node)
                .addInterface(second, node)
                .addInterface(second, resource)
                .addInterface(third, resource)
                .build()

        when:
        def nodeChanged = base.transform("nodeChanged", builder -> builder
                .removeInterface(first, node)
                .addInterface(third, node))
        def resourceChanged = base.transform("resourceChanged", builder ->
                builder.removeInterface(second, "Resource"))
        def cleared = base.transform("cleared", builder ->
                builder.clearInterfaces(second))

        then:
        base.getImplementations(node) == [first, second]
        base.getImplementations(resource) == [second, third]

        nodeChanged.getImplementations(node) == [second, third]
        nodeChanged.getImplementations(resource).is(base.getImplementations(resource))

        resourceChanged.getImplementations(node).is(base.getImplementations(node))
        resourceChanged.getImplementations(resource) == [third]

        cleared.getImplementations(node) == [first]
        cleared.getImplementations(resource) == [third]
    }

    def "unrelated and canonical no-op transforms share the complete index"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def object = universe.newObjectType("Object")
        def field = universe.newField("field")
        def string = universe.newScalarType("String")
        def base = universe.newSchema("base")
                .queryType(query)
                .addType(string)
                .addInterface(object, node)
                .build()

        when:
        def fieldChanged = base.transform("fieldChanged", builder -> builder
                .addField(object, field)
                .setFieldType(field, string))
        def duplicateRoundTrip = base.transform("duplicateRoundTrip", builder -> builder
                .addInterface(object, node)
                .removeInterface(object, node))
        def removeAndRestore = base.transform("removeAndRestore", builder -> builder
                .removeInterface(object, node)
                .addInterface(object, node))
        def removed = base.transform("removed", builder ->
                builder.removeInterface(object, node))

        then:
        fieldChanged.implementationsByInterfaceId.is(base.implementationsByInterfaceId)
        duplicateRoundTrip.implementationsByInterfaceId.is(base.implementationsByInterfaceId)
        removeAndRestore.implementationsByInterfaceId.is(base.implementationsByInterfaceId)
        fieldChanged.getImplementations(node).is(base.getImplementations(node))
        removed.getImplementations(node).isEmpty()
        removed.implementationsByInterfaceId.get(node.id) == null
    }

    def "foreign interface vertices are rejected"() {
        given:
        def first = new SchemaUniverse()
        def second = new SchemaUniverse()
        def query = first.newObjectType("Query")
        def foreign = second.newInterfaceType("Foreign")
        def schema = first.newSchema("schema")
                .queryType(query)
                .build()

        when:
        schema.getImplementations(foreign)

        then:
        thrown(AssertException)
    }

    def "SDL generation and GraphQLSchema import build the same implementation index"() {
        given:
        def sdl = '''
            interface Node {
                id: ID!
            }

            interface Resource implements Node {
                id: ID!
            }

            type Zebra implements Node {
                id: ID!
            }

            type Alpha implements Node & Resource {
                id: ID!
            }

            type Query {
                node: Node
            }
        '''
        def generated = new SchemaUniverse().parseSchema("generated", sdl)
        def imported = new SchemaUniverse().importSchema("imported", TestUtil.schema(sdl))

        expect:
        generated.getImplementations(generated.getInterfaceType("Node"))*.name ==
                ["Alpha", "Zebra"]
        generated.getImplementations(generated.getInterfaceType("Resource"))*.name ==
                ["Alpha"]
        imported.getImplementations(imported.getInterfaceType("Node"))*.name ==
                ["Alpha", "Zebra"]
        imported.getImplementations(imported.getInterfaceType("Resource"))*.name ==
                ["Alpha"]
    }
}
