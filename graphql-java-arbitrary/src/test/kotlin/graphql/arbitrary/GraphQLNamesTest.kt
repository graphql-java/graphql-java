@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.pair
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphQLNamesTest : ArbPropertyBase() {
    @Test
    fun `Arb_graphQLNames`(): Unit =
        runBlocking {
            Arb
                .int(min = 1, max = 1000)
                .forAll { count ->
                    val cfg = Config(SchemaSize(count))
                    val names = Arb.graphQLNames(cfg).bind()

                    val subtotal =
                        names.interfaces.size +
                            names.objects.size +
                            names.inputs.size +
                            names.unions.size +
                            names.scalars.size +
                            names.enums.size +
                            names.directives.size

                    subtotal == names.allNames.size
                }
        }

    @Test
    fun `Arb_graphQLNames -- IncludeCustomScalars`(): Unit =
        runBlocking {
            Arb
                .of(true, false)
                .flatMap { incl ->
                    val cfg = Config(GenCustomScalars(incl))
                    Arb
                        .graphQLNames(cfg)
                        .map { names -> incl to names.scalars - builtinScalars.keys }
                }.checkAll { (incl, scalars) ->
                    if (!incl) {
                        assertTrue(
                            scalars.isEmpty(),
                            "Found ${scalars.size} scalars when IncludeCustomScalars is $incl"
                        )
                    }
                }
        }

    @Test
    fun `Arb_graphQLNames -- TypeKindWeights -- zero`(): Unit =
        runBlocking {
            // all weights are 0
            val cfg = Config(
                TypeKindWeights(TypeKindWeights.zero),
                IncludeBuiltinScalars(false),
                IncludeBuiltinDirectives(false)
            )

            Arb.graphQLNames(cfg).forAll { names -> names.allNames.isEmpty() }
        }

    @Test
    fun `Arb_graphQLNames -- TypeKindWeights -- skewed weights`(): Unit =
        runBlocking {
            val ttw = TypeKindWeights.default + (TypeKind.Enum to 10.0) + (TypeKind.Union to 0.0)
            Arb
                .int(0 until 1_000)
                .flatMap { schemaSize ->
                    val cfg = Config(
                        TypeKindWeights(ttw),
                        SchemaSize(schemaSize)
                    )
                    Arb.graphQLNames(cfg)
                }.forAll { names ->
                    names.unions.isEmpty() && names.enums.size >= names.objects.size
                }
        }

    @Test
    fun `Arb_graphqlNames -- BanDirectiveNames`(): Unit =
        runBlocking {
            val dirName = "Directive_a"

            val cfg = Config(
                TypeNameLength(1.asIntRange()),
                SchemaSize(10),
                TypeKindWeights(TypeKindWeights.zero + (TypeKind.Directive to 1.0))
            )

            // sanity check that a "Directive_a" name *can* be generated
            assertTrue(
                Arb.graphQLNames(cfg)
                    .asSequence()
                    .take(100)
                    .any { names -> dirName in names.directives }
            )

            // assert that the name never gets generated when banned
            Arb.graphQLNames(cfg + BanDirectiveNames(setOf(dirName)))
                .forAll { names ->
                    dirName !in names.directives
                }
        }

    @Test
    fun `graphQLNames plus`(): Unit =
        runBlocking {
            Arb
                .pair(
                    Arb.graphQLNames(),
                    Arb.graphQLNames()
                ).forAll { (a, b) ->
                    (a.allNames + b.allNames) == (a + b).allNames
                }
        }

    @Test
    fun `filter`(): Unit =
        runBlocking {
            Arb.graphQLNames().forAll { names ->
                names.filter { false } == GraphQLNames.empty
            }

            Arb.graphQLNames().forAll { names ->
                names.filter { true } == names
            }
        }
}
