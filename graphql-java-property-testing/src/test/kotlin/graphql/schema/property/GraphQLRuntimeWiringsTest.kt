@file:Suppress("ForbiddenImport")

package graphql.schema.property

import graphql.ExceptionWhileDataFetching
import graphql.GraphQL
import graphql.execution.NonNullableFieldWasNullError
import graphql.language.Field
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.intRange
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.take
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GraphQLRuntimeWiringsTest : KotestPropertyBase() {
    @Test
    fun `returns the same data for the same query and same seed`(): Unit =
        runBlocking {
            val sdl = """
                interface I { x: Int }
                union Union = Query | Obj
                type Obj implements I { x:Int }
                type Query implements I { x:Int, u:[Union], i:[I] }
            """.trimIndent()
            Arb.long().forAll(1_000) { seed ->
                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed))
                val inp = Arb.graphQLExecutionInput(gql.graphQLSchema).bind()
                val results = (1..10).map { gql.execute(inp).toSpecification() }
                results.distinct().size == 1
            }
        }

    @Test
    fun `returns different data for the same query and different seeds`(): Unit =
        runBlocking {
            val sdl = """
            type Obj { x:Int! }
            type Query { obj:Obj! }
            """.trimIndent()
            val schema = sdl.asSchema

            // skip/include directives can make it hard to find a document that we expect to always select
            // interesting fields. Disable directives in generated docs
            val cfg = Config.default + (AppliedDirectiveWeight to CompoundingWeight.Never)

            Arb
                .graphQLDocument(schema, cfg)
                // require that queries select an interesting field
                .filter { it.allChildrenOfType<Field>().any { child -> child.name == "x" } }
                .flatMap { doc ->
                    arbitrary { rs ->
                        Arb
                            .long()
                            .take(100, rs)
                            .toList()
                            .map { seed ->
                                val input = Arb.graphQLExecutionInput(schema, doc, cfg).bind()
                                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg))
                                val result = gql.execute(input)
                                result.toSpecification()
                            }
                    }
                }.forAll(100) { results ->
                    results.toSet().size > 1
                }
        }

    @Test
    fun `can resolve __typename`(): Unit =
        runBlocking {
            val sdl = "type Query {x:Int}"
            val doc = "{ __typename }".asDocument

            Arb.long().forAll(100) { seed ->
                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed))
                val inp = Arb.graphQLExecutionInput(gql.graphQLSchema, doc).bind()
                val results = Arb
                    .constant(inp)
                    .map(gql::execute)
                    .take(10, randomSource)

                results.all {
                    it.toSpecification()["data"] == mapOf("__typename" to "Query")
                }
            }
        }

    @Test
    fun `returns the same value for fields in the same merge group`(): Unit =
        runBlocking {
            val sdl = "type Query {x: Int}"
            val doc = """
            {
                x
                x
                x @skip(if:false)
                x @include(if:true)
                ... on Query { x }
                ... { x }
                ... F
            }
            fragment F on Query { x }
            """.trimIndent().asDocument

            Arb.long().forAll(100) { seed ->
                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed))
                val inp = Arb.graphQLExecutionInput(gql.graphQLSchema, doc).bind()
                val results = Arb
                    .constant(inp)
                    .map(gql::execute)
                    .take(10, randomSource)

                results.all {
                    val data = requireNotNull(it.getData<Map<String, Any?>>())
                    // all keys should be merged and have the same value
                    data.size == 1 && data.values.distinct().size == 1
                }
            }
        }

    @Test
    fun `returns different values for fields in different merge groups`(): Unit =
        runBlocking {
            val sdl = "type Query {x: Int}"
            val doc = """
            {
                x
                a:x
                b:x @skip(if:false)
                c:x @include(if:true)
                ... on Query { d:x }
                ... { e:x }
                ... F
            }
            fragment F on Query { f:x }
            """.trimIndent().asDocument

            Arb.long().forAll(100) { seed ->
                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed))
                val inp = Arb.graphQLExecutionInput(gql.graphQLSchema, doc).bind()
                val results = Arb
                    .constant(inp)
                    .map(gql::execute)
                    .take(10, randomSource)

                results.all {
                    val data = requireNotNull(it.getData<Map<String, Any?>>())
                    // keys should not be in the same CollectFields group and should have distinct values
                    data.size == 7 && data.values.distinct().size > 1
                }
            }
        }

    @Test
    fun `type resolvers return referentially valid types`(): Unit =
        runBlocking {
            val sdl = """
                type Foo { x:Int }
                type Bar { x:Int }
                union Union = Foo | Bar
                type Query {u:Union}
            """.trimIndent()
            val doc = "{ u { __typename } }".asDocument

            Arb.long().forAll(100) { seed ->
                val wiring = arbRuntimeWiring(sdl, seed)
                    .let { w ->
                        val baseTypeResolver = w.typeResolvers["Union"]!!
                        w.transform {
                            // the setup for this test replaces a type resolver, which requires disabling strictMode
                            it.strictMode(false)

                            it.type("Union") {
                                it.typeResolver { env ->
                                    baseTypeResolver.getType(env).also {
                                        assertSame(env.schema.getObjectType(it.name), it)
                                    }
                                }
                            }
                        }
                    }

                val gql = mkGraphQL(sdl, wiring)
                val inp = Arb.graphQLExecutionInput(gql.graphQLSchema, doc).bind()
                val results = Arb
                    .constant(inp)
                    .map(gql::execute)
                    .take(10, randomSource)

                results.all { it.errors.isEmpty() }
            }
        }

    @Test
    fun `generates valid list values`(): Unit =
        runBlocking {
            val sdl = "type Query { x:[Int!]! }"
            val doc = "{x}".asDocument

            val cfg = Config.default
            Arb
                .long()
                .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                .flatMap { gql ->
                    val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                    input.map(gql::execute)
                }.forAll {
                    val data = requireNotNull(it.getData<Map<String, List<Any>>>())
                    data["x"]!!.all { v -> v is Int }
                }
        }

    @Test
    fun NullNonNullableWeight(): Unit =
        runBlocking {
            val sdl = "type Query { x:Int! }"
            val doc = "{x}".asDocument

            // disabled
            (Config.default + (NullNonNullableWeight to 0.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        it.errors.isEmpty()
                    }
            }

            // enabled
            (Config.default + (NullNonNullableWeight to 1.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        it.errors.any { it is NonNullableFieldWasNullError }
                    }
            }
        }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun ExplicitNullValueWeight(): Unit =
        runBlocking {
            val sdl = "type Query { x:Int }"
            val doc = "{x}".asDocument

            // disabled
            (Config.default + (ExplicitNullValueWeight to 0.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        val data = it.toSpecification()["data"] as Map<String, Any?>
                        data["x"] != null
                    }
            }

            // enabled
            (Config.default + (ExplicitNullValueWeight to 1.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        val data = it.toSpecification()["data"] as Map<String, Any?>
                        data["x"] == null
                    }
            }
        }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun ListValueSize(): Unit =
        runBlocking {
            val sdl = "type Query { x:[Int] }"
            val doc = "{x}".asDocument

            arbitrary { _ ->
                val seed = Arb.long().bind()
                val listSize = Arb
                    .intRange(0..100)
                    .filter { !it.isEmpty() }
                    .bind()
                val cfg = Config.default + (ListValueSize to listSize) + (ExplicitNullValueWeight to 0.0)
                val gql = mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg))
                val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg).bind()
                listSize to gql.execute(input)
            }.forAll { (listSize, result) ->
                val data = result.toSpecification()["data"] as Map<String, Any?>
                val x = data["x"] as List<Int>
                x.size in listSize
            }
        }

    @Test
    fun ResolverExceptionWeight(): Unit =
        runBlocking {
            val sdl = "type Query { x:[Int] }"
            val doc = "{x}".asDocument

            // disabled
            (Config.default + (ResolverExceptionWeight to 0.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        it.errors.isEmpty()
                    }
            }

            // enabled
            (Config.default + (ResolverExceptionWeight to 1.0)).let { cfg ->
                Arb
                    .long()
                    .map { seed -> mkGraphQL(sdl, arbRuntimeWiring(sdl, seed, cfg)) }
                    .flatMap { gql ->
                        val input = Arb.graphQLExecutionInput(gql.graphQLSchema, doc, cfg)
                        input.map(gql::execute)
                    }.forAll {
                        val err = (it.errors.firstOrNull() as? ExceptionWhileDataFetching)?.exception
                        err is ResolverInjectedException
                    }
            }
        }

    private fun mkGraphQL(
        sdl: String,
        wiring: RuntimeWiring
    ): GraphQL {
        val schema = SchemaGenerator().makeExecutableSchema(SchemaParser().parse(sdl), wiring)
        return GraphQL
            .newGraphQL(schema)
            .build()
    }
}
