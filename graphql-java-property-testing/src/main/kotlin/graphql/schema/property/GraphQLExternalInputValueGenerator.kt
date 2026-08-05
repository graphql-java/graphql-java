package graphql.schema.property

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

/**
 * Generates external variable values accepted by graphql-java for a GraphQL input type.
 *
 * Generation observes nullability, oneOf semantics, defaults, list coercion, and recursive input
 * object cycles. Unknown custom scalars require an entry in [ScalarValueOverrides].
 */
internal class GraphQLExternalInputValueGenerator(
    schema: GraphQLSchema,
    private val config: Config,
    private val randomSource: RandomSource,
    private val uncoercedValueWeight: Double = 0.0,
    private val allEdgesGraph: CycleGroups = CycleGroups.allInputCycles(schema),
    private val mandatoryEdgesGraph: CycleGroups = CycleGroups.mandatoryInputCycles(schema)
) {
    fun generate(type: GraphQLInputType): Any? =
        generate(ExternalValueGenerationContext(type = type, maxDepth = config[MaxValueDepth]))

    private fun generate(context: ExternalValueGenerationContext): Any? {
        val type = context.type
        if (type is GraphQLNonNull) {
            return generate(context.unwrap(type.wrappedType as GraphQLInputType, nullable = false))
        }
        if (context.nullable && shouldGenerateNull(context)) return null
        if (type is GraphQLList) return generateList(type, context)
        if (type is GraphQLScalarType) return generateScalar(type)
        if (type is GraphQLEnumType) return Arb.of(type.values).next(randomSource).name
        if (type is GraphQLInputObjectType && type.isOneOf) return generateOneOf(type, context)
        if (type is GraphQLInputObjectType) return generateInputObject(type, context)
        throw UnsupportedOperationException("Unsupported input type: $type")
    }

    private fun shouldGenerateNull(context: ExternalValueGenerationContext): Boolean =
        context.overBudget || randomSource.sampleWeight(config[ExplicitNullValueWeight])

    private fun generateList(
        listType: GraphQLList,
        context: ExternalValueGenerationContext
    ): Any? {
        val wrappedType = listType.wrappedType as GraphQLInputType
        val itemContext = context.push(wrappedType)
        val canUseSingleValueCoercion = GraphQLTypeUtil.unwrapNonNull(wrappedType) !is GraphQLList
        if (canUseSingleValueCoercion && !itemContext.overBudget && randomSource.sampleWeight(uncoercedValueWeight)) {
            return generate(context.push(wrappedType, nullable = false))
        }
        val size = if (itemContext.overBudget) 0 else Arb.int(config[ListValueSize]).next(randomSource)
        return List(size) { generate(itemContext) }
    }

    private fun generateScalar(type: GraphQLScalarType): Any? {
        config[ScalarValueOverrides][type.name]?.let { return it.next(randomSource) }
        return when (type.name) {
            "Boolean" -> randomSource.random.nextBoolean()
            "Float" -> generateFloat()
            "Int" -> randomSource.random.nextInt()
            "ID" -> generateId()
            "String" -> generateString()
            else -> throw UnsupportedOperationException(
                "No external value generator configured for scalar `${type.name}`"
            )
        }
    }

    private fun generateFloat(): Number =
        if (randomSource.sampleWeight(uncoercedValueWeight)) {
            randomSource.random.nextInt()
        } else {
            randomSource.random.nextDouble(-1_000_000.0, 1_000_000.0)
        }

    private fun generateId(): Any =
        if (randomSource.sampleWeight(uncoercedValueWeight)) {
            randomSource.random.nextInt()
        } else {
            generateString()
        }

    private fun generateString(): String = Arb.string(config[StringValueSize]).next(randomSource)

    private fun generateOneOf(
        type: GraphQLInputObjectType,
        context: ExternalValueGenerationContext
    ): Map<String, Any?> {
        val field = Arb.of(oneOfCandidates(type, context)).next(randomSource)
        return mapOf(field.name to generate(context.push(field.type, nullable = false)))
    }

    private fun oneOfCandidates(
        type: GraphQLInputObjectType,
        context: ExternalValueGenerationContext
    ): List<GraphQLInputObjectField> {
        if (!context.overBudget) return type.fields
        val cycleGroup = mandatoryEdgesGraph[type.name]
        return type.fields.filter { field ->
            val unwrapped = GraphQLTypeUtil.unwrapAll(field.type)
            unwrapped !is GraphQLInputObjectType ||
                GraphQLTypeUtil.unwrapNonNull(field.type) is GraphQLList ||
                unwrapped.name !in cycleGroup
        }.ifEmpty { type.fields }
    }

    private fun generateInputObject(
        type: GraphQLInputObjectType,
        context: ExternalValueGenerationContext
    ): Map<String, Any?> {
        val cycleGroup = allEdgesGraph[type.name]
        return type.fields
            .filter { shouldIncludeField(it, cycleGroup, context) }
            .associate { it.name to generate(context.push(it.type)) }
    }

    private fun shouldIncludeField(
        field: GraphQLInputObjectField,
        cycleGroup: Set<String>,
        context: ExternalValueGenerationContext
    ): Boolean {
        val inputType = GraphQLTypeUtil.unwrapAll(field.type) as? GraphQLInputObjectType
        if (inputType != null && inputType.name in cycleGroup) return true
        if (!field.hasSetDefaultValue() && field.type is GraphQLNonNull) return true
        return !context.overBudget && !randomSource.sampleWeight(config[ImplicitNullValueWeight])
    }
}

private data class ExternalValueGenerationContext(
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
    ): ExternalValueGenerationContext =
        copy(type = nextType, depth = depth + 1, nullable = nullable)

    fun unwrap(
        nextType: GraphQLInputType,
        nullable: Boolean
    ): ExternalValueGenerationContext = copy(type = nextType, nullable = nullable)
}
