@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll

class GraphQLNamesTest : FunSpec({
    test("allNames is the union of every name bucket") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(1..200)) { count ->
            val names = Arb.graphQLNames(Config.default + (SchemaSize to count)).bind()
            val union = names.interfaces + names.objects + names.inputs + names.unions +
                names.scalars + names.enums + names.directives
            names.allNames.shouldBe(union)
        }
    }

    test("GenCustomScalars controls generated custom scalar names") {
        listOf(true, false).forEach { include ->
            val config = Config.default + (GenCustomScalars to include)
            checkAll(PropTestConfig(iterations = 50), Arb.graphQLNames(config)) { names ->
                if (!include) (names.scalars - builtinScalars.keys).shouldBeEmpty()
            }
        }
    }

    test("zero weights produce no custom names") {
        val config = Config.default +
            (TypeTypeWeights to TypeTypeWeights.zero) +
            (IncludeBuiltinScalars to false) +
            (IncludeBuiltinDirectives to false)
        checkAll(PropTestConfig(iterations = 20), Arb.graphQLNames(config)) { names ->
            names.allNames.shouldBeEmpty()
        }
    }

    test("skewed weights control bucket populations") {
        val weights = TypeTypeWeights.zero + (TypeType.Enum to 10.0) + (TypeType.Object to 1.0)
        val configs = Arb.int(10..200).map { size ->
            Config.default + (SchemaSize to size) + (TypeTypeWeights to weights)
        }
        checkAll(PropTestConfig(iterations = 100), configs) { config ->
            val names = Arb.graphQLNames(config).bind()
            names.unions.shouldBeEmpty()
            (names.enums.size >= names.objects.size).shouldBe(true)
        }
    }

    test("BanDirectiveNames removes matching generated names") {
        val config = Config.default +
            (SchemaSize to 20) +
            (TypeNameLength to 1..1) +
            (TypeTypeWeights to (TypeTypeWeights.zero + (TypeType.Directive to 1.0))) +
            (BanDirectiveNames to setOf("Directive_a", "Directive_b"))
        checkAll(PropTestConfig(iterations = 100), Arb.graphQLNames(config)) { names ->
            names.directives.intersect(config[BanDirectiveNames]).shouldBeEmpty()
        }
    }

    test("plus unions buckets and filter retains matching names") {
        val pairs = Arb.pair(Arb.graphQLNames(), Arb.graphQLNames())
        checkAll(PropTestConfig(iterations = 100), pairs) { (first, second) ->
            (first + second).allNames.shouldBe(first.allNames + second.allNames)
            first.filter { true }.shouldBe(first)
            first.filter { false }.shouldBe(GraphQLNames.empty)
        }
    }
})
