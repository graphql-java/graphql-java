@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.Scalars
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

class GraphQLTypesTest : FunSpec({
    test("GraphQLTypesGen generates requested directives") {
        val cases = Arb.set(Arb.graphQLName(), 1..10).flatMap { directiveNames ->
            val names = GraphQLNames(mapOf(TypeType.Directive to directiveNames))
            Arb.graphQLTypes(names, minimalTypesConfig).map { directiveNames to it }
        }
        checkAll(typesPropertyConfig, cases) { (directiveNames, types) ->
            types.directives.keys.shouldContainAll(directiveNames)
        }
    }

    test("DirectiveIsRepeatable controls custom directives") {
        val cases = Arb.of(0.0, 1.0).flatMap { weight ->
            val config = minimalTypesConfig +
                (DirectiveIsRepeatable to weight) +
                (IncludeBuiltinDirectives to false)
            Arb.graphQLTypes(config).map { weight to it }
        }
        checkAll(typesPropertyConfig, cases) { (weight, types) ->
            types.directives.values.all { it.isRepeatable == (weight == 1.0) }.shouldBe(true)
        }
    }

    test("ListTypeWeight and NonNullableTypeWeight decorate types") {
        val compoundingWeights = Arb.pair(Arb.of(0.0, 1.0), Arb.int(0..5))
            .map { (weight, maximum) -> CompoundingWeight(weight, maximum) }
        checkAll(typesPropertyConfig, compoundingWeights, Arb.of(0.0, 1.0), Arb.long()) {
                listWeight, nonNullWeight, seed ->
            val names = GraphQLNames(mapOf(TypeType.Scalar to setOf("Int")))
            val config = minimalTypesConfig +
                (ListTypeWeight to listWeight) +
                (NonNullableTypeWeight to nonNullWeight)
            val type = GraphQLTypesGen(config, names, RandomSource.seeded(seed))
                .decorate(Scalars.GraphQLInt)
            val (lists, nonNulls) = type.decorationCounts()
            lists.shouldBe(if (listWeight.weight == 1.0) listWeight.max else 0)
            nonNulls.shouldBe(if (nonNullWeight == 1.0) lists + 1 else 0)
        }
    }

    test("DescriptionLength controls generated descriptions") {
        checkAll(typesPropertyConfig, Arb.int(0..50), Arb.long()) { length, seed ->
            val config = minimalTypesConfig + (DescriptionLength to length..length)
            GraphQLTypesGen(config, GraphQLNames.empty, RandomSource.seeded(seed))
                .genDescription()
                .length
                .shouldBe(length)
        }
    }

    test("FieldArgumentWeight controls field arguments") {
        val cases = Arb.pair(Arb.of(0.0, 1.0), Arb.int(0..3)).flatMap { (weight, maximum) ->
            val compoundingWeight = CompoundingWeight(weight, maximum)
            val config = minimalTypesConfig + (FieldArgumentWeight to compoundingWeight)
            Arb.graphQLTypes(config).map { compoundingWeight to it }
        }
        checkAll(typesPropertyConfig, cases) { (weight, types) ->
            val fields = (types.objects.values + types.interfaces.values).flatMap { it.fields }
            if (weight.weight == 1.0 && weight.max > 0 && fields.isNotEmpty()) {
                fields.any { it.arguments.isNotEmpty() }.shouldBe(true)
            } else {
                fields.all { it.arguments.isEmpty() }.shouldBe(true)
            }
        }
    }

    test("IncludeBuiltinScalars controls built-in scalars") {
        val cases = Arb.of(true, false).flatMap { include ->
            val config = minimalTypesConfig + (IncludeBuiltinScalars to include)
            Arb.graphQLTypes(config).map { include to it }
        }
        checkAll(typesPropertyConfig, cases) { (include, types) ->
            val present = builtinScalars.keys.intersect(types.scalars.keys)
            if (include) present.shouldContainAll(builtinScalars.keys) else present.shouldBeEmpty()
        }
    }

    test("IncludeBuiltinDirectives controls built-in directives") {
        val cases = Arb.of(true, false).flatMap { include ->
            val config = minimalTypesConfig + (IncludeBuiltinDirectives to include)
            Arb.graphQLTypes(config).map { include to it }
        }
        checkAll(typesPropertyConfig, cases) { (include, types) ->
            val present = builtinDirectives.keys.intersect(types.directives.keys)
            if (include) present.shouldContainAll(builtinDirectives.keys) else present.shouldBeEmpty()
        }
    }

    test("DirectiveHasArgs controls directive arguments") {
        val cases = Arb.pair(Arb.of(0.0, 1.0), Arb.int(0..3)).flatMap { (weight, maximum) ->
            val compoundingWeight = CompoundingWeight(weight, maximum)
            val config = minimalTypesConfig +
                (DirectiveHasArgs to compoundingWeight) +
                (IncludeBuiltinDirectives to false)
            Arb.graphQLTypes(config).map { compoundingWeight to it }
        }
        checkAll(typesPropertyConfig, cases) { (weight, types) ->
            if (weight.weight == 0.0 || weight.max == 0) {
                types.directives.values.all { it.arguments.isEmpty() }.shouldBe(true)
            } else if (types.directives.isNotEmpty()) {
                types.directives.values.any { it.arguments.isNotEmpty() }.shouldBe(true)
            }
        }
    }

    test("FieldNameLength controls field-like names") {
        val cases = Arb.int(2..20).flatMap { length ->
            val config = minimalTypesConfig + (FieldNameLength to length..length)
            Arb.graphQLTypes(config).map { length to it }
        }
        checkAll(typesPropertyConfig, cases) { (length, types) ->
            val names = types.objects.values.flatMap { it.fields }.map { it.name } +
                types.inputs.values.flatMap { it.fields }.map { it.name } +
                types.enums.values.flatMap { it.values }.map { it.name }
            names.filterNot { it.startsWith("escape_") }.all { it.length == length }.shouldBe(true)
        }
    }

    test("InputObjectTypeSize is an upper bound") {
        val cases = Arb.int(1..10).flatMap { size ->
            val config = minimalTypesConfig + (InputObjectTypeSize to size..size)
            Arb.graphQLTypes(config).map { size to it }
        }
        checkAll(typesPropertyConfig, cases) { (size, types) ->
            types.inputs.values.all { it.fields.size <= size }.shouldBe(true)
        }
    }

    test("InterfaceTypeSize is an upper bound") {
        val cases = Arb.int(1..10).flatMap { size ->
            val config = minimalTypesConfig + (InterfaceTypeSize to size..size)
            Arb.graphQLTypes(config).map { size to it }
        }
        checkAll(typesPropertyConfig, cases) { (size, types) ->
            types.interfaces.values.all { it.fields.size <= size }.shouldBe(true)
        }
    }

    test("ObjectTypeSize is an upper bound") {
        val cases = Arb.int(1..10).flatMap { size ->
            val config = minimalTypesConfig + (ObjectTypeSize to size..size)
            Arb.graphQLTypes(config).map { size to it }
        }
        checkAll(typesPropertyConfig, cases) { (size, types) ->
            types.objects.values.all { it.fields.size <= size }.shouldBe(true)
        }
    }

    test("UnionTypeSize is an upper bound") {
        val cases = Arb.int(1..10).flatMap { size ->
            val config = minimalTypesConfig + (UnionTypeSize to size..size)
            Arb.graphQLTypes(config).map { size to it }
        }
        checkAll(typesPropertyConfig, cases) { (size, types) ->
            types.unions.values.all { it.types.size <= size }.shouldBe(true)
        }
    }

    test("OneOfTypeWeight creates nullable oneOf fields without defaults") {
        checkAll(typesPropertyConfig, Arb.graphQLTypes(minimalTypesConfig + (OneOfTypeWeight to 1.0))) { types ->
            types.inputs.values.all { input ->
                input.isOneOf && input.fields.all { field ->
                    GraphQLTypeUtil.isNullable(field.type) && !field.hasSetDefaultValue()
                }
            }.shouldBe(true)
        }
    }

    test("enum values use their names as backing values") {
        val names = GraphQLNames(mapOf(TypeType.Enum to setOf("E")))
        checkAll(typesPropertyConfig, Arb.graphQLTypes(names, minimalTypesConfig + (EnumTypeSize to 1..3))) { types ->
            types.enums.values.all { enum -> enum.values.all { it.value == it.name } }.shouldBe(true)
        }
    }

    test("ObjectImplementsInterface controls object interfaces") {
        val names = GraphQLNames(
            mapOf(
                TypeType.Interface to setOf("I"),
                TypeType.Object to setOf("O"),
                TypeType.Scalar to setOf("Int")
            )
        )
        checkAll(typesPropertyConfig, Arb.of(false, true), Arb.long()) { enabled, seed ->
            val config = minimalTypesConfig +
                (ObjectImplementsInterface to if (enabled) CompoundingWeight.Once else CompoundingWeight.Never)
            val types = GraphQLTypesGen(config, names, RandomSource.seeded(seed)).gen()
            types.objects.values.all { it.interfaces.isNotEmpty() == enabled }.shouldBe(true)
        }
    }

    test("resolve finds present references") {
        val cases = Arb.graphQLNames().flatMap { names ->
            Arb.graphQLTypes(names, minimalTypesConfig).map { names to it }
        }
        checkAll(typesPropertyConfig, cases) { (names, types) ->
            names.names
                .filterKeys { it != TypeType.Directive }
                .values
                .flatten()
                .map(GraphQLTypeReference::typeRef)
                .all { types.resolve(it) != null }
                .shouldBe(true)
        }
    }

    test("resolve rejects missing references") {
        checkAll(typesPropertyConfig, Arb.graphQLTypes(minimalTypesConfig), Arb.graphQLName()) { types, name ->
            if (name !in types.names) {
                (types.resolve(GraphQLTypeReference.typeRef(name)) == null).shouldBe(true)
            }
        }
    }

    test("non-null recursive inputs receive escape fields") {
        val names = GraphQLNames(mapOf(TypeType.Input to setOf("A")))
        val config = minimalTypesConfig + (NonNullableTypeWeight to 1.0)
        checkAll(typesPropertyConfig, Arb.graphQLTypes(names, config)) { types ->
            types.inputs.getValue("A").fields.any {
                it.name.startsWith("escape_") && it.type is GraphQLScalarType
            }.shouldBe(true)
        }
    }

    test("uninhabited oneOf inputs receive escape fields") {
        val names = GraphQLNames(mapOf(TypeType.Input to setOf("A")))
        val config = minimalTypesConfig + (OneOfTypeWeight to 1.0)
        checkAll(typesPropertyConfig, Arb.graphQLTypes(names, config)) { types ->
            types.inputs.getValue("A").fields.any {
                it.name.startsWith("escape_") && it.type is GraphQLScalarType
            }.shouldBe(true)
        }
    }

    test("IncludeTypes retains every included type") {
        val cases = Arb.graphQLTypes(minimalTypesConfig).flatMap { included ->
            val config = minimalTypesConfig + (IncludeTypes to included)
            Arb.graphQLTypes(config).map { included to it }
        }
        checkAll(typesPropertyConfig, cases) { (included, generated) ->
            generated.names.containsAll(included.names).shouldBe(true)
        }
    }

    test("GenInterfaceStubsIfNeeded implements every interface") {
        val config = minimalTypesConfig + (GenInterfaceStubsIfNeeded to true)
        checkAll(typesPropertyConfig, Arb.graphQLTypes(config)) { types ->
            val implemented = types.objects.values.flatMap { objectType ->
                objectType.interfaces.map { it.name }
            }
            (types.interfaces.keys - implemented.toSet()).shouldBeEmpty()
        }
    }
})

private val typesPropertyConfig = PropTestConfig(iterations = 100)

/**
 * Most generation features are disabled so each property can enable only the behavior it tests.
 */
private val minimalTypesConfig = Config.default +
    (SchemaSize to 10) +
    (DefaultValueWeight to 0.0) +
    (AppliedDirectiveWeight to CompoundingWeight.Never) +
    (DirectiveHasArgs to CompoundingWeight.Never) +
    (FieldArgumentWeight to CompoundingWeight.Never) +
    (ListTypeWeight to CompoundingWeight.Never) +
    (InterfaceImplementsInterface to CompoundingWeight.Never) +
    (ObjectImplementsInterface to CompoundingWeight.Never) +
    (DescriptionLength to 0..0) +
    (InputObjectTypeSize to 1..1) +
    (ObjectTypeSize to 1..1) +
    (UnionTypeSize to 1..1) +
    (InterfaceTypeSize to 1..1) +
    (EnumTypeSize to 1..1) +
    (MaxValueDepth to 0) +
    (StringValueSize to 1..1) +
    (ListValueSize to 0..0) +
    (OneOfTypeWeight to 0.0)

private fun GraphQLType.decorationCounts(): Pair<Int, Int> {
    var lists = 0
    var nonNulls = 0
    var current = this
    while (GraphQLTypeUtil.isWrapped(current)) {
        when (current) {
            is GraphQLList -> lists++
            is GraphQLNonNull -> nonNulls++
        }
        current = GraphQLTypeUtil.unwrapOne(current)
    }
    return lists to nonNulls
}
