package graphql.schema.universe

import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import spock.lang.Specification

class VertexChunkAstTest extends Specification {

    def "AST sidecar supports empty definition-only and extension slots"() {
        given:
        def chunk = new VertexChunk(4)
        def definition = ObjectTypeDefinition.newObjectTypeDefinition()
                .name("Query")
                .build()
        def extension = ObjectTypeExtensionDefinition
                .newObjectTypeExtensionDefinition()
                .name("Query")
                .build()

        expect:
        chunk.getDefinition(0) == null
        chunk.getExtensionDefinitions(0).isEmpty()

        when:
        chunk.setAstDefinitions(0, definition, [])
        chunk.setAstDefinitions(1, definition, [extension])
        chunk.setAstDefinitions(2, null, [extension])
        chunk.setAstDefinitions(3, null, [])

        then:
        chunk.getDefinition(0).is(definition)
        chunk.getExtensionDefinitions(0).isEmpty()
        chunk.getDefinition(1).is(definition)
        chunk.getExtensionDefinitions(1) == [extension]
        chunk.getDefinition(2) == null
        chunk.getExtensionDefinitions(2) == [extension]
        chunk.getDefinition(3) == null
        chunk.getExtensionDefinitions(3).isEmpty()

        when:
        chunk.setAstDefinitions(1, null, [])

        then:
        chunk.getDefinition(1) == null
        chunk.getExtensionDefinitions(1).isEmpty()
    }

    def "reclaiming a vertex clears its AST slot"() {
        given:
        def chunk = new VertexChunk(1)
        def vertex = new SUObjectType(0, 1, "Query", null)
        def definition = ObjectTypeDefinition.newObjectTypeDefinition()
                .name("Query")
                .build()
        chunk.set(0, vertex)
        chunk.setAstDefinitions(0, definition, [])

        when:
        def removed = chunk.reclaimUnmarked(new BitSet(), 0, 1)

        then:
        removed == 1
        chunk.get(0) == null
        chunk.getDefinition(0) == null
        chunk.getExtensionDefinitions(0).isEmpty()
        !chunk.hasVertices()
    }

    def "schema options are immutable and reuse unchanged values"() {
        given:
        def defaults = SUSchemaOptions.defaultOptions()

        expect:
        defaults.captureAstDefinitions(true).is(defaults)
        defaults.captureAstDefinitions(false)
                .captureAstDefinitions(false)
                .isCaptureAstDefinitions() == false
    }
}
