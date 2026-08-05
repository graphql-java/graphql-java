@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.language.FragmentDefinition
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLUnionType
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GraphQLOutputValuesTest : KotestPropertyBase() {
    @Test
    fun `nullable and non-null scalar values honor ExplicitNullValueWeight`(): Unit =
        runBlocking {
            val schema = parseTestSchema("type Query { x: Int }")
            val scalar = schema.getType("Int") as GraphQLOutputType
            val nullConfig = Config.default + (ExplicitNullValueWeight to 1.0)
            Arb.graphQLOutputValue(schema, scalar, null, config = nullConfig).checkAll(20) {
                it.shouldBe(null)
            }
            val nonNull = GraphQLNonNull.nonNull(scalar)
            Arb.graphQLOutputValue(schema, nonNull, null, config = nullConfig).checkAll(20) {
                it.shouldBeInstanceOf<Int>()
            }
        }

    @Test
    fun `list wrappers preserve configured size and element nullability`(): Unit =
        runBlocking {
            val schema = parseTestSchema("type Query { x: Int }")
            val scalar = schema.getType("Int") as GraphQLOutputType
            val type = GraphQLNonNull.nonNull(GraphQLList.list(GraphQLNonNull.nonNull(scalar)))
            val config = Config.default +
                (ExplicitNullValueWeight to 1.0) +
                (ListValueSize to 2..2)
            Arb.graphQLOutputValue(schema, type, null, config = config).checkAll(20) { value ->
                val list = value.shouldBeInstanceOf<List<*>>()
                list.size.shouldBe(2)
                list.all { it is Int }.shouldBe(true)
            }
        }

    @Test
    fun `objects honor aliases, inline fragments, and named fragments`(): Unit =
        runBlocking {
            val schema = parseTestSchema("type Obj { x: Int!, y: Int! } type Query { obj: Obj! }")
            val document = Parser().parseDocument(
                "query Q { obj { alias: x ... on Obj { y } ...F } } fragment F on Obj { __typename }"
            )
            val config = Config.default + (ExplicitNullValueWeight to 0.0)
            Arb.graphQLOutputValue(schema, document, config).checkAll(30) { root ->
                root.typeName.shouldBe("Query")
                val obj = root.fields.getValue("obj").shouldBeInstanceOf<GeneratedObjectValue>()
                obj.fields.keys.toList().shouldContainExactly("alias", "y", "__typename")
                obj.fields["__typename"].shouldBe("Obj")
            }
        }

    @Test
    fun `SelectedTypeBias chooses a concrete type selected by a fragment`(): Unit =
        runBlocking {
            val schema = parseTestSchema(
                "type A { x: Int } type B { x: Int } union U = A | B type Query { u: U }"
            )
            val union = schema.getType("U") as GraphQLUnionType
            val selections = outputSelections("fragment F on U { ... on A { __typename } }")
            val config = Config.default +
                (SelectedTypeBias to 1.0) +
                (ExplicitNullValueWeight to 0.0)
            Arb.graphQLOutputValue(schema, union, selections, config = config).checkAll(50) { value ->
                value.shouldBeInstanceOf<GeneratedObjectValue>().typeName.shouldBe("A")
            }
        }

    @Test
    fun `custom scalar values use ScalarValueOverrides`(): Unit =
        runBlocking {
            val schema = parseTestSchema("scalar Custom type Query { x: Custom }")
            val scalar = schema.getType("Custom") as GraphQLOutputType
            val config = Config.default +
                (ExplicitNullValueWeight to 0.0) +
                (ScalarValueOverrides to mapOf("Custom" to Arb.constant("custom-value")))
            Arb.graphQLOutputValue(schema, scalar, null, config = config).checkAll(20) {
                it.shouldBe("custom-value")
            }
        }
}

private fun outputSelections(fragment: String): SelectionSet =
    Parser().parseDocument(fragment)
        .getDefinitionsOfType(FragmentDefinition::class.java)
        .single()
        .selectionSet
