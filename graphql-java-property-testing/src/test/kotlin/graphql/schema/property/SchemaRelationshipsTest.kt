package graphql.schema.property

import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLCompositeType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class SchemaRelationshipsTest : FunSpec({
    val schema = parseTestSchema(
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
    val relationships = SchemaRelationships(schema)

    test("possibleObjectTypes expands objects, interfaces, and unions") {
        val interfaceType = schema.getType("I") as GraphQLCompositeType
        val unionType = schema.getType("U") as GraphQLCompositeType
        val objectType = schema.getType("A") as GraphQLCompositeType
        relationships.possibleObjectTypes(interfaceType).map { it.name }.shouldBe(setOf("A", "B"))
        relationships.possibleObjectTypes(unionType).map { it.name }.shouldBe(setOf("A", "C"))
        relationships.possibleObjectTypes(objectType).map { it.name }.shouldBe(setOf("A"))
    }

    test("spreadability follows overlap of possible concrete types") {
        val interfaceType = schema.getType("I") as GraphQLCompositeType
        val unionType = schema.getType("U") as GraphQLCompositeType
        val a = schema.getType("A") as GraphQLCompositeType
        val b = schema.getType("B") as GraphQLCompositeType
        relationships.isSpreadable(interfaceType, unionType).shouldBe(true)
        relationships.isSpreadable(a, unionType).shouldBe(true)
        relationships.isSpreadable(b, unionType).shouldBe(false)
        relationships.spreadableTypes(a).shouldContain(interfaceType)
    }

    test("Schemas indexes directives by valid location") {
        val schemas = Schemas(schema)
        schemas.directivesByLocation.getValue(DirectiveLocation.FIELD)
            .map { it.name }
            .shouldContain("fieldDirective")
    }
})
