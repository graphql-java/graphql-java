package graphql.arbitrary

import graphql.schema.GraphQLSchema
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string

/** A generator that produces native String values for GraphQL ID scalars. */
fun interface IDValueGen {
    fun gen(): String

    /** A factory for producing [IDValueGen]s. */
    fun interface Factory {
        data class Params(
            val schema: GraphQLSchema,
            val cfg: Config,
            val rs: RandomSource
        )

        operator fun invoke(params: Params): IDValueGen

        /** Produces arbitrary strings with sizes bounded by [StringValueSize]. */
        object ArbString : Factory {
            override fun invoke(params: Params): IDValueGen =
                IDValueGen {
                    Arb.string(params.cfg[StringValueSize]).next(params.rs)
                }
        }

        companion object {
            /** The default ID value generator factory. */
            val default: Factory = ArbString
        }
    }
}
