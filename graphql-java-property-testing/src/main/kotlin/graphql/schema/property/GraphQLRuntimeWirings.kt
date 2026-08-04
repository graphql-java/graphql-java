package graphql.schema.property

import graphql.GraphQL
import graphql.TypeResolutionEnvironment
import graphql.execution.MergedField
import graphql.execution.ResultPath
import graphql.language.Field
import graphql.language.SelectionSet
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.idl.RuntimeWiring
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string

/**
 * Create a code registry that serves arbitrary data for [schema].
 *
 * Although the returned data is arbitrary, a given [seed] always produces the same data for
 * the same GraphQL document and variables. Supported configuration keys are
 * [ExplicitNullValueWeight], [ListValueSize], [NullNonNullableWeight],
 * [ResolverExceptionWeight], [ScalarValueOverrides], and [StringValueSize].
 */
fun arbitraryCodeRegistry(
    schema: GraphQLSchema,
    seed: Long,
    config: Config = Config.default
): GraphQLCodeRegistry = ArbitraryCodeRegistryGenerator(schema, seed, config).generate()

/** Create an executable [GraphQL] instance backed by deterministic arbitrary data. */
fun arbitraryGraphQL(
    schema: GraphQLSchema,
    seed: Long,
    config: Config = Config.default
): GraphQL {
    val executableSchema = schema.transform { builder ->
        builder.codeRegistry(arbitraryCodeRegistry(schema, seed, config))
    }
    return GraphQL.newGraphQL(executableSchema).build()
}

/**
 * Create a RuntimeWiring that serves arbitrary data for a schema.
 *
 * Though the data returned by this wiring is arbitrarily generated, the wiring
 * guarantees that for a given [seed] value it will always return the same data for a
 * given graphql document.
 *
 * The properties of the generated values can be configured in [cfg]. Supported [ConfigKey]s
 * are [ExplicitNullValueWeight], [ListValueSize], [NullNonNullableWeight],
 * [ResolverExceptionWeight], [ScalarValueOverrides], and [StringValueSize].
 */
fun arbRuntimeWiring(
    sdl: String,
    seed: Long,
    cfg: Config = Config.default
): RuntimeWiring {
    val schema = sdl.asSchema
    val codeRegistry = arbitraryCodeRegistry(schema, seed, cfg)
    val builder = RuntimeWiring.newRuntimeWiring()
    schema.allTypesAsList.forEach { type ->
        when (type) {
            is GraphQLObjectType -> builder.type(type.name) { typeBuilder ->
                type.fieldDefinitions.fold(typeBuilder) { current, field ->
                    current.dataFetcher(
                        field.name,
                        codeRegistry.getDataFetcher(FieldCoordinates.coordinates(type.name, field.name), field)
                    )
                }
            }

            is GraphQLInterfaceType -> builder.type(type.name) {
                it.typeResolver(codeRegistry.getTypeResolver(type))
            }

            is GraphQLUnionType -> builder.type(type.name) {
                it.typeResolver(codeRegistry.getTypeResolver(type))
            }
        }
    }
    return builder.build()
}

private class ArbitraryCodeRegistryGenerator(
    private val schema: GraphQLSchema,
    private val seed: Long,
    private val config: Config
) {
    private val relationships = SchemaRelationships(schema)

    fun generate(): GraphQLCodeRegistry {
        val builder = GraphQLCodeRegistry.newCodeRegistry(schema.codeRegistry)
        schema.allTypesAsList.forEach { type ->
            if (type.name.startsWith("__")) return@forEach
            when (type) {
                is GraphQLObjectType -> registerDataFetchers(builder, type)
                is GraphQLInterfaceType -> registerTypeResolver(builder, type)
                is GraphQLUnionType -> registerTypeResolver(builder, type)
            }
        }
        return builder.build()
    }

    private fun registerDataFetchers(
        builder: GraphQLCodeRegistry.Builder,
        type: GraphQLObjectType
    ) {
        type.fieldDefinitions.forEach { field ->
            val coordinates = FieldCoordinates.coordinates(type.name, field.name)
            builder.dataFetcher(coordinates, DataFetcher { environment: DataFetchingEnvironment ->
                val randomSource = saltedRandom(environment.stableHash)
                maybeThrowResolverException(randomSource)
                generateValue(environment.fieldType, randomSource)
            })
        }
    }

    private fun registerTypeResolver(
        builder: GraphQLCodeRegistry.Builder,
        type: GraphQLCompositeType
    ) {
        builder.typeResolver(type.name) { environment ->
            val randomSource = saltedRandom(environment.stableHash)
            maybeThrowResolverException(randomSource)
            val possibleTypes = relationships.possibleObjectTypes(type).toList()
            val selectedType = Arb.of(possibleTypes).next(randomSource)
            environment.schema.getObjectType(selectedType.name)
        }
    }

    private fun maybeThrowResolverException(randomSource: RandomSource) {
        if (randomSource.sampleWeight(config[ResolverExceptionWeight])) {
            throw ResolverInjectedException()
        }
    }

    private fun generateValue(type: GraphQLOutputType, randomSource: RandomSource): Any? {
        if (type is GraphQLNonNull && randomSource.sampleWeight(config[NullNonNullableWeight])) return null
        if (type !is GraphQLNonNull && randomSource.sampleWeight(config[ExplicitNullValueWeight])) return null

        val unwrappedType = GraphQLTypeUtil.unwrapNonNull(type) as GraphQLOutputType
        return when (unwrappedType) {
            is GraphQLList -> generateList(unwrappedType, randomSource)
            is GraphQLScalarType -> generateScalar(unwrappedType, randomSource)
            is GraphQLEnumType -> Arb.of(unwrappedType.values).next(randomSource).value
            else -> emptyMap<String, Any?>()
        }
    }

    private fun generateList(type: GraphQLList, randomSource: RandomSource): List<Any?> {
        val itemType = type.wrappedType as GraphQLOutputType
        val size = Arb.int(config[ListValueSize]).next(randomSource)
        return List(size) { generateValue(itemType, randomSource) }
    }

    private fun generateScalar(type: GraphQLScalarType, randomSource: RandomSource): Any? {
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

    private fun saltedRandom(salt: Long): RandomSource = RandomSource.seeded(seed xor salt)
}

private val MergedField.stableHash: Long
    get() = singleField.stableHash

private val Field.stableHash: Long
    get() = resultKey.hashCode().toLong()

private val SelectionSet.stableHash: Long
    get() = selections.filterIsInstance<Field>().fold(0L) { hash, field -> hash xor field.stableHash }

private val ResultPath.stableHash: Long
    get() = toString().hashCode().toLong()

private val DataFetchingEnvironment.stableHash: Long
    get() = arguments.hashCode().toLong() xor
        (field.selectionSet?.stableHash ?: 0L) xor
        executionStepInfo.path.stableHash

private val TypeResolutionEnvironment.stableHash: Long
    get() = arguments.hashCode().toLong() xor field.stableHash

/** Exception deliberately thrown when [ResolverExceptionWeight] selects an injected failure. */
internal class ResolverInjectedException : RuntimeException(
    "Injected exception, see ResolverExceptionWeight"
)
