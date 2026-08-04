package graphql.schema.universe

import graphql.AssertException
import graphql.schema.InputValueWithState
import spock.lang.Specification

class SchemaUniverseCleanupTest extends Specification {

    def "cleanup reclaims removed and detached vertices without affecting registered schemas"() {
        given:
        def universe = new SchemaUniverse()
        def sharedString = universe.newScalarType("String")
        def firstQuery = universe.newObjectType("FirstQuery")
        def firstField = universe.newField("first")
        def first = universe.newSchema("first")
                .queryType(firstQuery)
                .addField(firstQuery, firstField)
                .setFieldType(firstField, sharedString)
                .build()
        def secondQuery = universe.newObjectType("SecondQuery")
        def secondField = universe.newField("second")
        def second = universe.newSchema("second")
                .queryType(secondQuery)
                .addField(secondQuery, secondField)
                .setFieldType(secondField, sharedString)
                .build()
        def detached = universe.newEnumType("Detached")
        def firstRoot = first.root

        when:
        universe.removeSchema(first)
        def reclaimed = universe.cleanupUnusedVertices()

        then:
        reclaimed == 4
        universe.vertexCount == 4
        universe.cleanupUnusedVertices() == 0
        universe.owns(sharedString)
        universe.owns(second.root)
        universe.owns(secondQuery)
        universe.owns(secondField)
        !universe.owns(firstRoot)
        !universe.owns(firstQuery)
        !universe.owns(firstField)
        !universe.owns(detached)
        second.queryType.is(secondQuery)
        second.getField(secondQuery, "second").is(secondField)
        second.getType(secondField).is(sharedString)

        when:
        universe.getVertex(firstQuery.id)

        then:
        thrown(AssertException)

        when:
        def next = universe.newEnumType("Next")

        then:
        next.id > detached.id
        universe.vertexCount == 5
        universe.getVertex(next.id).is(next)
    }

    def "cleanup retains dormant adjacency stored by a registered schema"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def extra = universe.newObjectType("Extra")
        def field = universe.newField("value")
        def string = universe.newScalarType("String")
        def base = universe.newSchema("base")
                .queryType(query)
                .addAdditionalType(extra)
                .addField(extra, field)
                .setFieldType(field, string)
                .build()
        def withoutExtra = base.transform(
                "withoutExtra",
                builder -> builder.removeAdditionalType(extra))
        def baseRoot = base.root

        expect:
        universe.cleanupUnusedVertices() == 0

        when:
        universe.removeSchema(base)
        def reclaimed = universe.cleanupUnusedVertices()

        then:
        reclaimed == 1
        !universe.owns(baseRoot)
        universe.owns(extra)
        universe.owns(field)
        universe.owns(string)
        withoutExtra.getType("Extra") == null

        when:
        def reattached = withoutExtra.transform(
                "reattached",
                builder -> builder.addAdditionalType(extra))

        then:
        reattached.getObjectType("Extra").is(extra)
        reattached.getField(extra, "value").is(field)
        reattached.getType(field).is(string)
    }

    def "cleanup retains types referenced by applied directive arguments"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def string = universe.newScalarType("String")
        def orphan = universe.newScalarType("Orphan")
        def argument = universe.newAppliedDirectiveArgument(
                "value",
                string,
                InputValueWithState.newExternalValue("tagged"))
        def appliedDirective = universe.newAppliedDirective("tag", [argument])
        def schema = universe.newSchema("schema")
                .queryType(query)
                .addAppliedDirective(query, appliedDirective)
                .build()

        when:
        def reclaimed = universe.cleanupUnusedVertices()

        then:
        reclaimed == 1
        !universe.owns(orphan)
        universe.owns(string)
        schema.getType(argument).is(string)
        schema.getAppliedDirectives(query) == [appliedDirective]
    }

    def "cleanup releases empty chunks and allocation continues above reclaimed ids"() {
        given:
        def universe = new SchemaUniverse()
        def vertices = (0..1024).collect {
            universe.newField("field${it}")
        }
        def lastId = vertices.last().id

        when:
        def reclaimed = universe.cleanupUnusedVertices()

        then:
        reclaimed == 1025
        universe.vertexCount == 0
        !universe.owns(vertices.first())
        !universe.owns(vertices.last())

        when:
        universe.getVertex(lastId)

        then:
        thrown(AssertException)

        when:
        def next = universe.newObjectType("Next")

        then:
        next.id == lastId + 1
        universe.vertexCount == 1
        universe.getVertex(next.id).is(next)

        when:
        def schema = universe.newSchema("next")
                .queryType(next)
                .build()

        then:
        universe.cleanupUnusedVertices() == 0
        universe.vertexCount == 2
        schema.queryType.is(next)
    }
}
