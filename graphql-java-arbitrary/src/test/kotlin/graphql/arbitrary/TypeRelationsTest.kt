package graphql.arbitrary

import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLCompositeType
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// JMB TODO: not nearly enough tests
class TypeRelationsTest {
    private val schema = parseTestSchema(
        """
            directive @fieldDirective on FIELD
            interface I { x: Int }
            type A implements I { x: Int }
            type B implements I { x: Int }
            type C { x: Int }
            union U = A | C
            type Query { i: I, u: U }
        """.trimIndent()
    )
    private val rels = TypeRelations(schema)

    @Test
    fun `possibleObjectTypes expands objects, interfaces, and unions`() {
        val interfaceType = schema.getType("I") as GraphQLCompositeType
        val unionType = schema.getType("U") as GraphQLCompositeType
        val objectType = schema.getType("A") as GraphQLCompositeType
        rels.possibleObjectTypes(interfaceType).map { it.name }.shouldBe(setOf("A", "B"))
        rels.possibleObjectTypes(unionType).map { it.name }.shouldBe(setOf("A", "C"))
        rels.possibleObjectTypes(objectType).map { it.name }.shouldBe(setOf("A"))
    }

    @Test
    fun `spreadability follows overlap of possible concrete types`() {
        val interfaceType = schema.getType("I") as GraphQLCompositeType
        val unionType = schema.getType("U") as GraphQLCompositeType
        val a = schema.getType("A") as GraphQLCompositeType
        val b = schema.getType("B") as GraphQLCompositeType
        rels.isSpreadable(interfaceType, unionType).shouldBe(true)
        rels.isSpreadable(a, unionType).shouldBe(true)
        rels.isSpreadable(b, unionType).shouldBe(false)
        rels.spreadableTypes(a).shouldContain(interfaceType)
    }

    @Test
    fun `Schemas indexes directives by valid location`() {
        val schemas = Schemas(schema)
        schemas.directivesByLocation.getValue(DirectiveLocation.FIELD)
            .map { it.name }
            .shouldContain("fieldDirective")
    }
}
