package graphql.schema.universe

import graphql.AssertException
import graphql.schema.InputValueWithState
import spock.lang.Specification

class SUSchemaMetadataTest extends Specification {

    def "schema stores an immutable copy of user metadata"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def field = universe.newField("value")
        def mutableValue = []
        def supplied = [owner: "platform", payload: mutableValue]
        def builder = universe.newSchema("schema")
                .queryType(query)
                .addField(query, field)
                .vertexMetadata(query, supplied)
                .vertexMetadata(query, "temporary", true)
                .removeVertexMetadata(query, "temporary")
                .vertexMetadata(field, "role", "leaf")

        when:
        supplied.owner = "changed"
        supplied.extra = "late"
        def schema = builder.build()

        then:
        schema.getVertexMetadata(query) == [
                owner: "platform",
                payload: mutableValue
        ]
        schema.getVertexMetadata(query, "owner") == "platform"
        schema.getVertexMetadata(query, "missing") == null
        schema.getVertexMetadata(field) == [role: "leaf"]
        schema.getVertexMetadata(query).payload.is(mutableValue)

        when:
        schema.getVertexMetadata(query).put("forbidden", true)

        then:
        thrown(UnsupportedOperationException)

        when:
        def replacementValue = []
        def derived = schema.transform(
                "derived",
                derivedBuilder -> derivedBuilder.vertexMetadata(query, [
                        owner: "platform",
                        payload: replacementValue
                ]))

        then:
        replacementValue == mutableValue
        !replacementValue.is(mutableValue)
        derived.getVertexMetadata(query).payload.is(replacementValue)
    }

    def "metadata is versioned per schema and root metadata follows transformations"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def field = universe.newField("value")
        def copy = universe.newField("copy")
        def baseBuilder = universe.newSchema("base")
        def baseRoot = baseBuilder.root
        def base = baseBuilder
                .queryType(query)
                .addField(query, field)
                .vertexMetadata(baseRoot, [owner: "root"])
                .vertexMetadata(query, [scope: "base", retained: true])
                .vertexMetadata(field, [stable: "value"])
                .build()

        when:
        def unchanged = base.transform("unchanged", builder -> {
        })
        def derived = base.transform("derived", builder -> builder
                .vertexMetadata(query, "scope", "derived")
                .removeVertexMetadata(query, "retained")
                .clearVertexMetadata(field)
                .copyVertexMetadata(query, copy))

        then:
        base.getVertexMetadata(query) == [scope: "base", retained: true]
        unchanged.getVertexMetadata(query).is(base.getVertexMetadata(query))
        derived.getVertexMetadata(query) == [scope: "derived"]
        derived.getVertexMetadata(copy) == [scope: "derived"]
        base.getVertexMetadata(field) == [stable: "value"]
        derived.getVertexMetadata(field).isEmpty()
        base.getVertexMetadata(baseRoot) == [owner: "root"]
        derived.getVertexMetadata(baseRoot).isEmpty()
        derived.getVertexMetadata(derived.root) == [owner: "root"]
    }

    def "metadata-only vertices and their intrinsic type references survive cleanup"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def tagged = universe.newEnumType("Tagged")
        def orphan = universe.newEnumType("Orphan")
        def string = universe.newScalarType("String")
        def argument = universe.newAppliedDirectiveArgument(
                "value",
                string,
                InputValueWithState.newExternalValue("metadata"))
        def directive = universe.newAppliedDirective("tag", [argument])
        def schema = universe.newSchema("schema")
                .queryType(query)
                .vertexMetadata(tagged, [
                        reason: "detached",
                        opaqueReference: orphan
                ])
                .vertexMetadata(directive, [reason: "directive"])
                .build()

        when:
        def reclaimed = universe.cleanupUnusedVertices()

        then:
        reclaimed == 1
        !universe.owns(orphan)
        universe.owns(tagged)
        universe.owns(directive)
        universe.owns(string)
        schema.getVertexMetadata(tagged) == [
                reason: "detached",
                opaqueReference: orphan
        ]
        schema.getVertexMetadata(directive) == [reason: "directive"]
        schema.getType(argument).is(string)

        when:
        def retainedVertexCount = universe.vertexCount
        universe.removeSchema(schema)

        then:
        universe.cleanupUnusedVertices() == retainedVertexCount
        universe.vertexCount == 0
    }

    def "metadata builder rejects foreign vertices and null map contents"() {
        given:
        def universe = new SchemaUniverse()
        def otherUniverse = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def foreign = otherUniverse.newObjectType("Foreign")
        def builder = universe.newSchema("schema").queryType(query)

        when:
        builder.vertexMetadata(foreign, [key: "value"])

        then:
        thrown(AssertException)

        when:
        builder.vertexMetadata(query, (Map<String, Object>) null)

        then:
        thrown(AssertException)

        when:
        builder.vertexMetadata(query, [(null): "value"])

        then:
        thrown(AssertException)

        when:
        builder.vertexMetadata(query, [key: null])

        then:
        thrown(AssertException)

        when:
        def schema = builder.vertexMetadata(query, [key: "value"]).build()
        schema.getVertexMetadata(foreign)

        then:
        thrown(AssertException)

        when:
        builder.clearVertexMetadata(query)

        then:
        thrown(AssertException)
    }
}
