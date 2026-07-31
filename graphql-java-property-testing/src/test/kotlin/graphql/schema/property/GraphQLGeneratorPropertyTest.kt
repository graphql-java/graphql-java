@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.Scalars
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

class GraphQLGeneratorPropertyTest : FunSpec({
    test("generated names satisfy the GraphQL name grammar") {
        val namePattern = Regex("^[_A-Za-z][_0-9A-Za-z]*$")
        checkAll(Arb.graphQLName()) { name ->
            namePattern.matches(name).shouldBe(true)
            name.startsWith("__").shouldBe(false)
            name.shouldNotBe("Boolean")
            name.shouldNotBe("Float")
            name.shouldNotBe("ID")
            name.shouldNotBe("Int")
            name.shouldNotBe("String")
        }
    }

    test("generated enum names exclude reserved values") {
        checkAll(Arb.graphQLEnumValueName()) { name ->
            (name in setOf("true", "false", "null")).shouldBe(false)
        }
    }

    test("schema names are unique across type categories") {
        checkAll(PropTestConfig(iterations = 250), Arb.int(1..100)) { size ->
            val names = Arb.graphQLNames(Config.default + (SchemaSize to size)).bind()
            val subtotal = names.interfaces.size +
                names.objects.size +
                names.inputs.size +
                names.unions.size +
                names.scalars.size +
                names.enums.size +
                names.directives.size
            subtotal.shouldBe(names.allNames.size)
        }
    }

    test("zero type weights generate no arbitrary names") {
        val config = Config.default +
            (TypeTypeWeights to TypeTypeWeights.zero) +
            (IncludeBuiltinScalars to false) +
            (IncludeBuiltinDirectives to false)
        checkAll(Arb.graphQLNames(config)) { names ->
            names.allNames.shouldBeEmpty()
        }
    }

    test("type decorators honor deterministic list and non-null weights") {
        val names = GraphQLNames(mapOf(TypeType.Scalar to setOf("Int")))
        val listDepth = Arb.int(0..4)
        val nonNull = Arb.of(true, false)
        checkAll(listDepth, nonNull) { depth, useNonNull ->
            val config = Config.default +
                (ListTypeWeight to CompoundingWeight(1.0, depth)) +
                (NonNullableTypeWeight to if (useNonNull) 1.0 else 0.0)
            val decorated = GraphQLTypesGen(config, names, io.kotest.property.RandomSource.default())
                .decorate(Scalars.GraphQLInt)
            countLists(decorated).shouldBe(depth)
            countNonNulls(decorated).shouldBe(if (useNonNull) depth + 1 else 0)
        }
    }

    test("generated oneOf inputs are nullable and inhabited") {
        val config = Config.default +
            (OneOfTypeWeight to 1.0) +
            (SchemaSize to 30) +
            (TypeTypeWeights to TypeTypeWeights.zero + (TypeType.Input to 1.0))
        checkAll(PropTestConfig(iterations = 100), Arb.graphQLTypes(config)) { types ->
            types.inputs.values.forEach { input ->
                input.isOneOf.shouldBe(true)
                input.fields.forEach { field -> field.type.isNullable().shouldBe(true) }
                input.fields.shouldNotBeEmpty()
            }
        }
    }

    test("included types remain available to generated schemas") {
        val included = GraphQLTypes.empty.copy(
            scalars = mapOf(Scalars.GraphQLInt.name to Scalars.GraphQLInt)
        )
        checkAll(Arb.graphQLTypes(Config.default + (IncludeTypes to included))) { types ->
            types.scalars.keys.shouldContainAll(included.scalars.keys)
        }
    }
})

private fun GraphQLType.isNullable(): Boolean = this !is GraphQLNonNull

private fun countLists(type: GraphQLType): Int {
    var current = type
    var count = 0
    while (GraphQLTypeUtil.isWrapped(current)) {
        if (current is GraphQLList) count++
        current = GraphQLTypeUtil.unwrapOne(current)
    }
    return count
}

private fun countNonNulls(type: GraphQLType): Int {
    var current = type
    var count = 0
    while (GraphQLTypeUtil.isWrapped(current)) {
        if (current is GraphQLNonNull) count++
        current = GraphQLTypeUtil.unwrapOne(current)
    }
    return count
}
