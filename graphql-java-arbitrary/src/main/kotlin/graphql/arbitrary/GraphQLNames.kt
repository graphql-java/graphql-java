package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.withEdgecases

/** Bucketed sets of names that can be used in building GraphQL types */
class GraphQLNames internal constructor(
    internal val names: Map<TypeKind, Set<String>>
) {
    operator fun plus(other: GraphQLNames): GraphQLNames =
        GraphQLNames(
            TypeKind
                .values()
                .associateWith { nt -> ((names[nt] ?: emptySet()) + (other.names[nt] ?: emptySet())) }
        )

    val interfaces: Set<String>
        get() = names[TypeKind.Interface] ?: emptySet()

    val objects: Set<String>
        get() = names[TypeKind.Object] ?: emptySet()

    val inputs: Set<String>
        get() = names[TypeKind.Input] ?: emptySet()

    val unions: Set<String>
        get() = names[TypeKind.Union] ?: emptySet()

    val scalars: Set<String>
        get() = names[TypeKind.Scalar] ?: emptySet()

    val enums: Set<String>
        get() = names[TypeKind.Enum] ?: emptySet()

    val directives: Set<String>
        get() = names[TypeKind.Directive] ?: emptySet()

    val allNames: Set<String>
        get() = names.values.flatten().toSet()

    fun filter(fn: (String) -> Boolean): GraphQLNames {
        val filtered = names.mapValues { (_, values) -> values.filter(fn).toSet() }
        if (filtered.values.all { it.isEmpty() }) return empty
        return GraphQLNames(filtered)
    }

    override fun equals(other: Any?): Boolean = (other as? GraphQLNames)?.names == names

    override fun hashCode(): Int = names.hashCode()

    override fun toString(): String = names.toString()

    companion object {
        val empty: GraphQLNames = GraphQLNames(emptyMap())

        private fun List<String>.prefix(nameType: TypeKind): List<String> =
            this.map {
                buildString {
                    append(nameType.name)
                    append('_')
                    append(it)
                }
            }

        /**
         * Generate a GraphQLNames from a pool of raw names, like ["Foo", "Bar"]
         * The returned GraphQLNames will transform the names to include a prefix, like
         *   ["Object_Foo", "Scalar_Bar"]
         */
        fun fromRawNames(
            names: List<String>,
            cfg: Config = Config.default
        ): GraphQLNames {
            tailrec fun loop(
                acc: Map<TypeKind, Set<String>>,
                pool: List<String>,
                typeKindCounts: List<Pair<TypeKind, Int>>
            ): GraphQLNames =
                if (typeKindCounts.isNotEmpty()) {
                    val (tt, count) = typeKindCounts.first()
                    val entry = tt to pool.take(count).prefix(tt).toSet()
                    loop(
                        acc = acc + entry,
                        pool = pool.drop(count),
                        typeKindCounts = typeKindCounts.drop(1),
                    )
                } else {
                    GraphQLNames(acc)
                }

            val typeKinds =
                TypeKind.values().let { tts ->
                    tts.filter {
                        when (it) {
                            TypeKind.Scalar -> cfg[GenCustomScalars]
                            else -> true
                        }
                    }
                }
            val typeKindCounts = typeKinds
                .associateWith { tt -> cfg[TypeKindWeights][tt] ?: 1.0 }
                .let { ttWeights ->
                    // normalize
                    val total = ttWeights.values.sum()
                    ttWeights.mapValues { (_, weight) ->
                        val normWeight = weight / total
                        (names.size * normWeight).toInt()
                    }
                }
            return loop(
                acc = emptyMap(),
                pool = names,
                typeKindCounts = typeKindCounts.toList()
            )
        }
    }
}

/**
 * Generate a [GraphQLNames] from a provided Config
 * The generated GraphQLNames will have a length close to the value of [SchemaSize]
 */
fun Arb.Companion.graphQLNames(cfg: Config = Config.default): Arb<GraphQLNames> =
    Arb
        .set(
            Arb.graphQLName(cfg[TypeNameLength]),
            cfg[SchemaSize]
        ).map {
            GraphQLNames.fromRawNames(it.toList(), cfg)
        }.withEdgecases(GraphQLNames.empty)
        .map { names ->
            if (cfg[IncludeBuiltinScalars]) {
                names + GraphQLNames(mapOf(TypeKind.Scalar to builtinScalars.keys))
            } else {
                names
            }
        }.map { names ->
            if (cfg[IncludeBuiltinDirectives]) {
                names + GraphQLNames(mapOf(TypeKind.Directive to builtinDirectives.keys))
            } else {
                names
            }
        }.map { names ->
            val ban = cfg[BanDirectiveNames]
            if (ban.isNotEmpty()) {
                val filteredDirNames = names.names[TypeKind.Directive]
                    ?.filter { !ban.contains(it) }
                    ?.toSet()
                    ?: emptySet()
                GraphQLNames(names.names + (TypeKind.Directive to filteredDirNames))
            } else {
                names
            }
        }.map { names ->
            val extant = cfg[IncludeTypes].names
            if (extant.isNotEmpty()) {
                names.filter { !extant.contains(it) }
            } else {
                names
            }
        }