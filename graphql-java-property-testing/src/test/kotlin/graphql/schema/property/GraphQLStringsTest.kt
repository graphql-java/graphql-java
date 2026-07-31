@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

class GraphQLStringsTest : FunSpec({
    test("graphQLName generates spec-compliant non-introspection names") {
        val pattern = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
        checkAll(PropTestConfig(iterations = 500), Arb.graphQLName(1..40)) { name ->
            name.matches(pattern).shouldBe(true)
            name.startsWith("__").shouldBe(false)
        }
    }

    test("graphQLName produces valid type names") {
        val placeholder = GraphQLFieldDefinition.newFieldDefinition()
            .name("placeholder")
            .type(Scalars.GraphQLInt)
            .build()
        checkAll(PropTestConfig(iterations = 100), Arb.graphQLName()) { name ->
            val query = GraphQLObjectType.newObject().name(name).field(placeholder).build()
            runCatching { GraphQLSchema.newSchema().query(query).build() }.isSuccess.shouldBe(true)
        }
    }

    test("field and argument names are valid and start lower-case when alphabetic") {
        val generators = listOf(Arb.graphQLFieldName(), Arb.graphQLArgumentName())
        generators.forEach { generator ->
            checkAll(PropTestConfig(iterations = 100), generator) { name ->
                name.startsWith("__").shouldBe(false)
                val first = name.first()
                (!first.isLetter() || first.isLowerCase()).shouldBe(true)
            }
        }
    }

    test("enum value names exclude reserved values") {
        checkAll(PropTestConfig(iterations = 200), Arb.graphQLEnumValueName()) { name ->
            (name !in setOf("true", "false", "null") && !name.startsWith("__")).shouldBe(true)
        }
    }

    test("BanFieldNames affects all field-like name generators") {
        val banned = ('a'..'m').flatMap { listOf(it.toString(), it.uppercase()) }.toSet()
        val config = Config.default + (BanFieldNames to banned) + (FieldNameLength to 1..2)
        val generators = listOf(
            Arb.graphQLFieldName(config),
            Arb.graphQLArgumentName(config),
            Arb.graphQLEnumValueName(config)
        )
        generators.forEach { generator ->
            checkAll(PropTestConfig(iterations = 100), generator) { name ->
                (name !in banned).shouldBe(true)
            }
        }
    }
})
