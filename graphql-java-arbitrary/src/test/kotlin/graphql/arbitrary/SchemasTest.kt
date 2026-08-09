package graphql.arbitrary

import graphql.Directives
import graphql.introspection.Introspection.DirectiveLocation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemasTest {
    @Test
    fun `directivesByLocation -- empty`() {
        val schemas = Schemas("type Query { x: Int }".asSchema)
        // GraphQL built in directives change somewhat often.
        // To keep this test low maintenance, let's just spot check a couple key ones
        listOf(
            Directives.OneOfDirective,
            Directives.SkipDirective,
            Directives.IncludeDirective,
            Directives.DeprecatedDirective
        ).forEach { dir ->
            dir.validLocations().forEach { loc ->
                assertTrue(schemas.directivesByLocation[loc]!!.any { it.name == dir.name })
            }
        }
    }

    @Test
    fun `directivesByLocation -- custom`() {
        val schemas = Schemas(
            """
                directive @foo on FIELD
                directive @bar on FIELD | OBJECT
                type Query { x: Int }
            """.trimIndent().asSchema
        )
        schemas.directivesByLocation[DirectiveLocation.FIELD]!!.any { it.name == "foo" }
        schemas.directivesByLocation[DirectiveLocation.FIELD]!!.any { it.name == "bar" }
        schemas.directivesByLocation[DirectiveLocation.OBJECT]!!.any { it.name == "bar" }
    }
}
