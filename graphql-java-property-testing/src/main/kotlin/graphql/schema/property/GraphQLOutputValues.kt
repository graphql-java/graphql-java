package graphql.schema.property

import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.Selection
import graphql.language.SelectionSet
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string

/** A generated GraphQL object result and the concrete type represented by that result. */
data class GeneratedObjectValue(
    val typeName: String,
    val fields: Map<String, Any?>
)

/**
 * Generate a native result value for [type] containing exactly the requested [selections].
 *
 * Supported configuration keys are [ExplicitNullValueWeight], [ListValueSize],
 * [ScalarValueOverrides], [SelectedTypeBias], and [StringValueSize].
 */
fun Arb.Companion.graphQLOutputValue(
    schema: GraphQLSchema,
    type: GraphQLOutputType,
    selections: SelectionSet?,
    fragments: Map<String, FragmentDefinition> = emptyMap(),
    config: Config = Config.default
): Arb<Any?> =
    arbitrary { randomSource ->
        GraphQLOutputValueGenerator(schema, config, randomSource)
            .generate(type, selections, fragments)
    }

/** Generate a native result value for one operation in [document]. */
fun Arb.Companion.graphQLOutputValue(
    schema: GraphQLSchema,
    document: Document,
    config: Config = Config.default
): Arb<GeneratedObjectValue> =
    arbitrary { randomSource ->
        val fragments = document.getDefinitionsOfType(FragmentDefinition::class.java)
            .associateBy { requireNotNull(it.name) }
        val operations = document.getDefinitionsOfType(OperationDefinition::class.java)
        require(operations.isNotEmpty()) { "Document must define at least one operation" }
        val operation = Arb.of(operations).next(randomSource)
        val rootType = operation.rootType(schema)
        GraphQLOutputValueGenerator(schema, config, randomSource)
            .generate(rootType, operation.selectionSet, fragments) as GeneratedObjectValue
    }

private class GraphQLOutputValueGenerator(
    private val schema: GraphQLSchema,
    private val config: Config,
    private val randomSource: RandomSource
) {
    private val relationships = SchemaRelationships(schema)

    fun generate(
        type: GraphQLOutputType,
        selections: SelectionSet?,
        fragments: Map<String, FragmentDefinition>
    ): Any? = generate(type, selections, fragments, nullable = true)

    private fun generate(
        type: GraphQLOutputType,
        selections: SelectionSet?,
        fragments: Map<String, FragmentDefinition>,
        nullable: Boolean
    ): Any? {
        if (type is GraphQLNonNull) {
            return generate(type.wrappedType as GraphQLOutputType, selections, fragments, nullable = false)
        }
        if (nullable && randomSource.sampleWeight(config[ExplicitNullValueWeight])) return null
        if (type is GraphQLList) return generateList(type, selections, fragments)
        if (type is GraphQLObjectType) return generateObject(type, requireNotNull(selections), fragments)
        if (type is GraphQLCompositeType) {
            val concreteType = concretize(type, requireNotNull(selections), fragments)
            return generateObject(concreteType, selections, fragments)
        }
        if (type is GraphQLEnumType) return Arb.of(type.values).next(randomSource).value
        if (type is GraphQLScalarType) return generateScalar(type)
        throw UnsupportedOperationException("Unsupported output type: $type")
    }

    private fun generateList(
        type: GraphQLList,
        selections: SelectionSet?,
        fragments: Map<String, FragmentDefinition>
    ): List<Any?> {
        val itemType = type.wrappedType as GraphQLOutputType
        val size = Arb.int(config[ListValueSize]).next(randomSource)
        return List(size) { generate(itemType, selections, fragments, nullable = true) }
    }

    private fun generateObject(
        type: GraphQLObjectType,
        selections: SelectionSet,
        fragments: Map<String, FragmentDefinition>
    ): GeneratedObjectValue {
        val fields = linkedMapOf<String, Any?>()
        selections.selections.forEach { selection ->
            fields.putAll(generateSelection(type, selection, fragments))
        }
        return GeneratedObjectValue(type.name, fields)
    }

    private fun generateSelection(
        type: GraphQLObjectType,
        selection: Selection<*>,
        fragments: Map<String, FragmentDefinition>
    ): Map<String, Any?> =
        when (selection) {
            is Field -> generateField(type, selection, fragments)
            is FragmentSpread -> generateFragment(type, fragments.requireFragment(selection.name), fragments)
            is InlineFragment -> generateInlineFragment(type, selection, fragments)
            else -> throw IllegalArgumentException("Unexpected selection type: $selection")
        }

    private fun generateField(
        type: GraphQLObjectType,
        field: Field,
        fragments: Map<String, FragmentDefinition>
    ): Map<String, Any?> {
        if (field.name == "__typename") return mapOf(field.resultKey to type.name)
        val definition = requireNotNull(type.getFieldDefinition(field.name)) {
            "Unexpected field: ${type.name}.${field.name}"
        }
        val value = generate(definition.type, field.selectionSet, fragments, nullable = true)
        return mapOf(field.resultKey to value)
    }

    private fun generateFragment(
        type: GraphQLObjectType,
        fragment: FragmentDefinition,
        fragments: Map<String, FragmentDefinition>
    ): Map<String, Any?> {
        val fragmentType = schema.requireCompositeType(requireNotNull(fragment.typeCondition.name))
        if (!relationships.isSpreadable(type, fragmentType)) return emptyMap()
        return generateObject(type, fragment.selectionSet, fragments).fields
    }

    private fun generateInlineFragment(
        type: GraphQLObjectType,
        fragment: InlineFragment,
        fragments: Map<String, FragmentDefinition>
    ): Map<String, Any?> {
        val fragmentType = fragment.typeCondition?.name?.let(schema::requireCompositeType) ?: type
        if (!relationships.isSpreadable(type, fragmentType)) return emptyMap()
        return generateObject(type, fragment.selectionSet, fragments).fields
    }

    private fun concretize(
        type: GraphQLCompositeType,
        selections: SelectionSet,
        fragments: Map<String, FragmentDefinition>
    ): GraphQLObjectType {
        if (type is GraphQLObjectType) return type
        val possibleTypes = relationships.possibleObjectTypes(type)
        require(possibleTypes.isNotEmpty()) {
            "Cannot generate a value for abstract type `${type.name}`: no implementations found"
        }
        if (randomSource.sampleWeight(config[SelectedTypeBias])) {
            val selectedTypes = selectedObjectTypes(selections, fragments).intersect(possibleTypes)
            if (selectedTypes.isNotEmpty()) return Arb.of(selectedTypes.toList()).next(randomSource)
        }
        return Arb.of(possibleTypes.toList()).next(randomSource)
    }

    private fun selectedObjectTypes(
        selections: SelectionSet,
        fragments: Map<String, FragmentDefinition>
    ): Set<GraphQLObjectType> {
        val selectedTypes = linkedSetOf<GraphQLObjectType>()
        val pending = ArrayDeque<Selection<*>>()
        pending.addAll(selections.selections)
        while (pending.isNotEmpty()) {
            when (val selection = pending.removeFirst()) {
                is Field -> Unit
                is InlineFragment -> {
                    val selectedType = selection.typeCondition?.name
                        ?.let(schema::requireCompositeType) as? GraphQLObjectType
                    selectedType?.let(selectedTypes::add)
                    pending.addAll(selection.selectionSet.selections)
                }
                is FragmentSpread -> {
                    val fragment = fragments.requireFragment(selection.name)
                    val selectedType = schema.requireCompositeType(requireNotNull(fragment.typeCondition.name))
                    if (selectedType is GraphQLObjectType) selectedTypes.add(selectedType)
                    pending.addAll(fragment.selectionSet.selections)
                }
            }
        }
        return selectedTypes
    }

    private fun generateScalar(type: GraphQLScalarType): Any? {
        config[ScalarValueOverrides][type.name]?.let { return it.next(randomSource) }
        return when (type.name) {
            "Boolean" -> randomSource.random.nextBoolean()
            "Float" -> randomSource.random.nextDouble(-1_000_000.0, 1_000_000.0)
            "Int" -> randomSource.random.nextInt()
            "ID" -> Arb.string(config[StringValueSize]).next(randomSource)
            "String" -> Arb.string(config[StringValueSize]).next(randomSource)
            else -> throw UnsupportedOperationException(
                "No external value generator configured for scalar `${type.name}`"
            )
        }
    }
}

private fun Map<String, FragmentDefinition>.requireFragment(name: String): FragmentDefinition =
    requireNotNull(this[name]) { "Missing fragment `$name`" }

private fun GraphQLSchema.requireCompositeType(name: String): GraphQLCompositeType =
    requireNotNull(getType(name) as? GraphQLCompositeType) { "Missing composite type `$name`" }

private fun OperationDefinition.rootType(schema: GraphQLSchema): GraphQLObjectType =
    when (operation) {
        OperationDefinition.Operation.QUERY -> schema.queryType
        OperationDefinition.Operation.MUTATION -> requireNotNull(schema.mutationType) {
            "Mutation operation requested but schema does not define a mutation type"
        }
        OperationDefinition.Operation.SUBSCRIPTION -> requireNotNull(schema.subscriptionType) {
            "Subscription operation requested but schema does not define a subscription type"
        }
    }
