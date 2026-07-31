package graphql.schema.property

import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Generates GraphQL language AST literals that are valid inputs for a schema type.
 *
 * Generation observes nullability, oneOf semantics, list coercion, defaults, and recursive input
 * object cycles. [uncoercedValueWeight] controls valid literals that rely on input coercion.
 */
internal class GraphQLInputValueGenerator(
    schema: GraphQLSchema,
    private val config: Config,
    private val randomSource: RandomSource,
    private val uncoercedValueWeight: Double = 0.0,
    private val allEdgesGraph: CycleGroups = CycleGroups.mandatoryInputCycles(schema),
    private val mandatoryEdgesGraph: CycleGroups = CycleGroups.mandatoryInputCycles(schema)
) {
    fun generate(type: GraphQLInputType): Value<*> =
        generate(ValueGenerationContext(type = type, maxDepth = config[MaxValueDepth]))

    private fun generate(context: ValueGenerationContext): Value<*> {
        val type = context.type
        if (type is GraphQLNonNull) {
            return generate(context.unwrap(type.wrappedType as GraphQLInputType, nullable = false))
        }
        if (context.nullable && shouldGenerateNull(context)) {
            return NullValue.newNullValue().build()
        }
        if (type is GraphQLList) return generateList(type, context)
        if (type is GraphQLScalarType) return generateScalar(type)
        if (type is GraphQLEnumType) return generateEnum(type)
        if (type is GraphQLInputObjectType && type.isOneOf) {
            return generateOneOf(type, context)
        }
        if (type is GraphQLInputObjectType) return generateInputObject(type, context)
        throw UnsupportedOperationException("Unsupported input type: $type")
    }

    private fun shouldGenerateNull(context: ValueGenerationContext): Boolean =
        context.overBudget || randomSource.sampleWeight(config[ExplicitNullValueWeight])

    private fun generateList(
        listType: GraphQLList,
        context: ValueGenerationContext
    ): Value<*> {
        val wrappedType = listType.wrappedType as GraphQLInputType
        val itemContext = context.push(wrappedType)
        val itemCanUseSingleValueCoercion = GraphQLTypeUtil.unwrapNonNull(wrappedType) !is GraphQLList
        if (itemCanUseSingleValueCoercion && !itemContext.overBudget && randomSource.sampleWeight(uncoercedValueWeight)) {
            // A non-list literal is coerced as a single list item. Null is the exception: it
            // represents a null list, so it is not valid after a non-null list has been selected.
            return generate(context.push(wrappedType, nullable = false))
        }
        val size = if (itemContext.overBudget) 0 else Arb.int(config[ListValueSize]).next(randomSource)
        val values = List(size) { generate(itemContext) }
        return ArrayValue.newArrayValue().values(values).build()
    }

    private fun generateScalar(type: GraphQLScalarType): Value<*> {
        config[ScalarLiteralOverrides][type.name]?.let { return it.next(randomSource) }
        return when (type.name) {
            "Boolean" -> BooleanValue.newBooleanValue(randomSource.random.nextBoolean()).build()
            "Float" -> generateFloat()
            "Int" -> IntValue.newIntValue(BigInteger.valueOf(randomSource.random.nextInt().toLong())).build()
            "ID" -> generateId()
            "String" -> generateString()
            else -> generateString()
        }
    }

    private fun generateFloat(): Value<*> {
        if (randomSource.sampleWeight(uncoercedValueWeight)) {
            return IntValue.newIntValue(BigInteger.valueOf(randomSource.random.nextInt().toLong())).build()
        }
        val value = randomSource.random.nextDouble(-1_000_000.0, 1_000_000.0)
        return FloatValue.newFloatValue(BigDecimal.valueOf(value)).build()
    }

    private fun generateId(): Value<*> {
        if (randomSource.sampleWeight(uncoercedValueWeight)) {
            return IntValue.newIntValue(BigInteger.valueOf(randomSource.random.nextInt().toLong())).build()
        }
        return generateString()
    }

    private fun generateString(): StringValue =
        StringValue.newStringValue(Arb.string(config[StringValueSize]).next(randomSource)).build()

    private fun generateEnum(type: GraphQLEnumType): EnumValue {
        val enumValue = Arb.of(type.values).next(randomSource)
        return EnumValue.newEnumValue(enumValue.name).build()
    }

    private fun generateOneOf(
        type: GraphQLInputObjectType,
        context: ValueGenerationContext
    ): ObjectValue {
        val candidates = oneOfCandidates(type, context)
        val field = Arb.of(candidates).next(randomSource)
        val value = generate(context.push(field.type, nullable = false))
        return objectValue(field.name, value)
    }

    private fun oneOfCandidates(
        type: GraphQLInputObjectType,
        context: ValueGenerationContext
    ): List<GraphQLInputObjectField> {
        if (!context.overBudget) return type.fields
        val cycleGroup = mandatoryEdgesGraph[type.name]
        val exits = type.fields.filter { field ->
            val unwrapped = GraphQLTypeUtil.unwrapAll(field.type)
            unwrapped !is GraphQLInputObjectType ||
                GraphQLTypeUtil.unwrapNonNull(field.type) is GraphQLList ||
                unwrapped.name !in cycleGroup
        }
        return exits.ifEmpty { type.fields }
    }

    private fun generateInputObject(
        type: GraphQLInputObjectType,
        context: ValueGenerationContext
    ): ObjectValue {
        val cycleGroup = allEdgesGraph[type.name]
        val fields = type.fields.filter { field ->
            shouldIncludeField(field, cycleGroup, context)
        }.map { field ->
            ObjectField.newObjectField()
                .name(field.name)
                .value(generate(context.push(field.type)))
                .build()
        }
        return ObjectValue.newObjectValue().objectFields(fields).build()
    }

    private fun shouldIncludeField(
        field: GraphQLInputObjectField,
        cycleGroup: Set<String>,
        context: ValueGenerationContext
    ): Boolean {
        val inputType = GraphQLTypeUtil.unwrapAll(field.type) as? GraphQLInputObjectType
        if (inputType != null && inputType.name in cycleGroup) return true
        if (!field.hasSetDefaultValue() && field.type is GraphQLNonNull) return true
        return !context.overBudget && !randomSource.sampleWeight(config[ImplicitNullValueWeight])
    }

    private fun objectValue(name: String, value: Value<*>): ObjectValue =
        ObjectValue.newObjectValue()
            .objectField(ObjectField.newObjectField().name(name).value(value).build())
            .build()
}

private data class ValueGenerationContext(
    val type: GraphQLInputType,
    val depth: Int = 0,
    val maxDepth: Int,
    val nullable: Boolean = true
) {
    val overBudget: Boolean
        get() = depth >= maxDepth

    fun push(
        nextType: GraphQLInputType,
        nullable: Boolean = nextType !is GraphQLNonNull
    ): ValueGenerationContext =
        copy(type = nextType, depth = depth + 1, nullable = nullable)

    fun unwrap(
        nextType: GraphQLInputType,
        nullable: Boolean
    ): ValueGenerationContext = copy(type = nextType, nullable = nullable)
}
