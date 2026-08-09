@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import graphql.ParseAndValidate
import graphql.language.Argument
import graphql.language.AstPrinter
import graphql.language.Directive
import graphql.language.DirectivesContainer
import graphql.language.Document
import graphql.language.Field
import graphql.language.FloatValue
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.IntValue
import graphql.language.NonNullType
import graphql.language.OperationDefinition
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.schema.GraphQLSchema
import graphql.validation.ValidationError
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.take
import io.kotest.property.assume
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class GraphQLDocumentGenTest : ArbPropertyBase(
    iterations = 5_000
) {
    private val schema =
        """
            directive @dir(arg:Int!) repeatable on QUERY | MUTATION | SUBSCRIPTION | FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | VARIABLE_DEFINITION
            input Input { a:Int, b:[Int!]!, c:[[[Int]]] }
            type Foo implements I1 { x:Int, next:Union, i1(inp:Input!):I1! }
            type Bar implements I1 & I2 { x:Int, y:Int, next:Union }
            union Union = Foo | Bar
            interface I1 { x:Int }
            interface I2 { y:Int }
            type Query { x(a:Int!):Int, u:Union, y(inp:Input!): Union }
            type Mutation { x:Int, y:Int }
            type Subscription { x:Int, y:Int }
        """.trimIndent().asSchema

    private fun mkConfig(
        aliasWeight: Double = 0.0,
        anonymousOperationWeight: Double = 0.0,
        banDirectiveNames: Set<String> = emptySet(),
        banSelectionCoordinates: Set<TypeOrFieldCoordinate> = emptySet(),
        documentUncoercedValueWeight: Double = 0.0,
        directiveWeight: CompoundingWeight = CompoundingWeight.Never,
        explicitNullValueWeight: Double = 0.0,
        fieldNameLength: Int = 4,
        fieldSelectionWeight: CompoundingWeight = CompoundingWeight.Once,
        fragmentDefinitionWeight: Double = 0.0,
        fragmentSpreadWeight: CompoundingWeight = CompoundingWeight.Never,
        implicitNullValueWeight: Double = 0.0,
        inlineFragmentWeight: CompoundingWeight = CompoundingWeight.Never,
        maxSelectionSetDepth: Int = 1,
        operationCount: Int = 1,
        typeNameLength: Int = 4,
        untypedInlineFragmentWeight: Double = 0.0,
        variableWeight: Double = 0.0,
    ): Config =
        Config(
            AliasWeight(aliasWeight),
            AnonymousOperationWeight(anonymousOperationWeight),
            AppliedDirectiveWeight(directiveWeight),
            BanDirectiveNames(banDirectiveNames),
            BanSelectionCoordinates(banSelectionCoordinates),
            DocumentUncoercedValueWeight(documentUncoercedValueWeight),
            ExplicitNullValueWeight(explicitNullValueWeight),
            FieldNameLength(fieldNameLength.asIntRange()),
            FieldSelectionWeight(fieldSelectionWeight),
            FragmentDefinitionWeight(fragmentDefinitionWeight),
            FragmentSpreadWeight(fragmentSpreadWeight),
            ImplicitNullValueWeight(implicitNullValueWeight),
            InlineFragmentWeight(inlineFragmentWeight),
            MaxSelectionSetDepth(maxSelectionSetDepth),
            OperationCount(operationCount.asIntRange()),
            TypeNameLength(typeNameLength.asIntRange()),
            UntypedInlineFragmentWeight(untypedInlineFragmentWeight),
            VariableWeight(variableWeight)
        )

    @Test
    fun `manual inspection`(): Unit =
        // this test makes no assertions but is useful for visually inspecting
        // the generated documents to see that they are being generated as we
        // expect
        runBlocking {
            Arb.graphQLDocument(schema).checkAll(10) { doc ->
                assertEquals(emptyList<Any>(), validate(schema, doc))

                val docString = AstPrinter.printAst(doc)
                print(docString)
                println("-----")
                // set a breakpoint on the next line
                markSuccess()
            }
        }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents with minimal config for trivial schema`() {
        assertAllDocumentsValid("type Query { x:Int }", mkConfig())
    }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents with default config for trivial schema`() {
        assertAllDocumentsValid("type Query { x:Int }", Config.default)
    }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents with default config`() {
        assertAllDocumentsValid(schema, Config.default)
    }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents for schemas with default values`() {
        val schema = """
            directive @dir(inp:Inp = {x:2}) repeatable on QUERY | MUTATION | SUBSCRIPTION | FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | VARIABLE_DEFINITION
            input Inp { x:Int = 1, y:Int! = 2, z:Int = null }
            type Query {
                x(inp: Inp = { y: 3, z:0 }): Int
            }
        """.trimIndent().asSchema
        assertAllDocumentsValid(schema, Config.default)
    }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents for arbitrary schemas`() {
        // use the default config, but dial it down to keep this test from being too slow
        val cfg = Config(
            SchemaSize(20),
            FragmentSpreadWeight(CompoundingWeight(.2, 1)),
            InlineFragmentWeight(CompoundingWeight(.2, 1))
        )

        // our test matrix is N schemas by M documents. To get good coverage while being in
        // the ballpark of `iterations`, let's use a square root value for each M/N dimension
        val iter = sqrt(iterations.toDouble()).toInt()

        // GJ schemas
        Arb
            .graphQLSchema(cfg)
            .take(iter, randomSource)
            .forEach { schema ->
                assertAllDocumentsValid(schema, cfg, iter)
            }

    }

    @Test
    fun `Arb_graphQLDocument -- generates valid documents with minimal config`() {
        assertAllDocumentsValid(schema, mkConfig())
    }

    @Test
    fun `Arb_graphQLDocument -- merged field subselections`() =
        assertAllDocumentsValid(
            """
                type Obj { x:Int, next:Obj, others: [Obj] }
                type Query { obj:Obj }
            """.trimIndent()
        )

    @Test
    fun `Arb_graphQLDocument -- merged field subselections with interfaces`() =
        assertAllDocumentsValid(
            """
                interface I { i:I }
                type Obj implements I { x:Int, i:I }
                type Query { i: I }
            """.trimIndent()
        )

    @Test
    fun `Arb_graphQLDocument -- union selections`() =
        assertAllDocumentsValid(
            """
                type Foo { x:Int, u:U }
                type Bar { y:Int, u:U }
                union U = Foo | Bar
                type Query { u:U }
            """.trimIndent(),
            cfg = Config(FragmentSpreadWeight(CompoundingWeight.Never))
        )

    @Test
    fun `Arb_graphQLDocument -- cyclic input objects`() =
        assertAllDocumentsValid(
            """
                input Input { x:Int, i:Input }
                type Query { x(inp:Input!):Int }
            """.trimIndent()
        )

    @Test
    fun `Arb_graphQLDocument -- cyclic output objects`() =
        assertAllDocumentsValid(
            """
                type Obj { obj:Obj! }
                type Query { obj:Obj! }
            """.trimIndent()
        )

    @Test
    fun `Arb_graphQLDocument -- schemas with subscriptions`() =
        assertAllDocumentsValid(
            """
                type Obj { x:Int, y:Obj }
                type Subscription { x:Int, obj:Obj }
                type Query { x:Int }
            """.trimIndent()
        )

    @Test
    fun `Arb_graphQLDocument -- schemas with subscriptions and incremental directives`(): Unit =
        runBlocking {
            mkConfig(directiveWeight = CompoundingWeight.Always).let { cfg ->
                val schema = """
                    directive @defer(if: Boolean = true, label: String) on FRAGMENT_SPREAD | INLINE_FRAGMENT
                    directive @stream(if: Boolean = true, label: String, initialCount: Int = 0) on FIELD
                    type Obj { x:Int, y:Obj }
                    type Subscription { x:Int, obj:Obj }
                    type Query { x:Int }
                """.trimIndent().asSchema

                // incremental directives may not appear anywhere in a subscription operation
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc
                        .getDefinitionsOfType(OperationDefinition::class.java)
                        .filter { it.operation == OperationDefinition.Operation.SUBSCRIPTION }
                        .all { op ->
                            op.allChildrenOfType<Directive>().none { it.name == "defer" || it.name == "stream" }
                        }
                }
            }
        }

    @Test
    fun `Arb_graphQLDocument -- schemas with mutations and incremental directives`(): Unit =
        runBlocking {
            mkConfig(directiveWeight = CompoundingWeight.Always).let { cfg ->
                val schema = """
                directive @defer(if: Boolean = true, label: String) on FRAGMENT_SPREAD | INLINE_FRAGMENT
                directive @stream(if: Boolean = true, label: String, initialCount: Int = 0) on FIELD
                type Obj { x:Int, y:Obj }
                type Mutation { x:Int, obj:Obj }
                type Query { x:Int }
                """.trimIndent().asSchema

                // incremental directives may not be used on a mutation root selection
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc
                        .getDefinitionsOfType(OperationDefinition::class.java)
                        .filter { it.operation == OperationDefinition.Operation.MUTATION }
                        .flatMap { it.selectionSet.selections }
                        .mapNotNull { it as? DirectivesContainer<*> }
                        .all { sel ->
                            sel.directives.none { it.name == "defer" || it.name == "stream" }
                        }
                }
            }
        }

    @Test
    fun AliasWeight() {
        runBlocking {
            // disabled
            mkConfig(aliasWeight = 0.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<Field>().all { it.alias == null }
                }
            }

            // enabled
            mkConfig(aliasWeight = 1.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<Field>().all { it.alias != null }
                }
            }
        }
    }

    @Test
    fun AnonymousOperationWeight() {
        runBlocking {
            // disabled
            mkConfig(anonymousOperationWeight = 0.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    val operations = it.getDefinitionsOfType(OperationDefinition::class.java)
                    operations.isNotEmpty() && operations.all { op -> op.name != null }
                }
            }

            // enabled
            mkConfig(anonymousOperationWeight = 1.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    val operations = it.getDefinitionsOfType(OperationDefinition::class.java)
                    operations.size == 1 && operations[0].name == null
                }
            }
        }
    }

    @Test
    fun BanSelectionCoordinates(): Unit =
        runBlocking {
            val schema = "type Query { x:Int }".asSchema

            // disabled
            mkConfig(banSelectionCoordinates = emptySet()).let { cfg ->
                Arb.graphQLDocument(schema, cfg)
                    .asSequence(randomSource)
                    .take(1000)
                    .any {
                        val fields = it.allChildrenOfType<Field>()
                        fields.any { it.name == "x" }
                    }
            }

            // enabled
            mkConfig(banSelectionCoordinates = setOf("Query" to "x")).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    val fields = it.allChildrenOfType<Field>()
                    fields.none { it.name == "x" }
                }
            }
        }

    @Test
    fun DirectiveWeight(): Unit =
        runBlocking {
            // disabled
            mkConfig(directiveWeight = CompoundingWeight.Never).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    it.allChildrenOfType<DirectivesContainer<*>>().all { child -> child.directives.isEmpty() }
                }
            }

            // enabled
            mkConfig(directiveWeight = CompoundingWeight.Once).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    it.allChildrenOfType<DirectivesContainer<*>>().all { child -> child.directives.isNotEmpty() }
                }
            }
        }

    @Test
    fun `DocumentUncoercedValueWeight`(): Unit =
        runBlocking {
            val schema = "extend type Query { field(arg: Float!):Int }".asPermissiveTestSchema

            // disabled
            mkConfig(documentUncoercedValueWeight = 0.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    it.allChildrenOfType<Argument>().all { arg -> arg.value is FloatValue }
                }
            }

            // enabled
            mkConfig(documentUncoercedValueWeight = 1.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    it.allChildrenOfType<Argument>().all { arg -> arg.value is IntValue }
                }
            }
        }

    @Test
    fun BanDirectiveNames(): Unit =
        runBlocking {
            // enabled
            mkConfig(directiveWeight = CompoundingWeight.Once, banDirectiveNames = setOf("dir")).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll {
                    it.allChildrenOfType<Directive>().none { d -> d.name == "dir" }
                }
            }
        }

    @Test
    fun `FieldNameLength -- alias names`(): Unit =
        runBlocking {
            val schema = "type Query { x: Int }".asSchema

            arbitrary {
                val length = Arb.int(1..10).bind()
                val cfg = mkConfig(aliasWeight = 1.0, fieldNameLength = length)
                length to Arb.graphQLDocument(schema, cfg).bind()
            }.forAll { (length, doc) ->
                val fields = doc
                    .allChildrenOfType<Field>()
                    // the alias generator will try to generate some alias names that collide with field names
                    // which will ignore the FieldNameLength config. Filter these out
                    .filter { it.alias != "x" && it.alias != "__typename" }

                fields.all { it.alias?.length == length }
            }
        }

    @Test
    fun FragmentDefinitionWeight(): Unit =
        runBlocking {
            val schema = "type Query { x:Int }".asSchema

            // enabled: bias to new fragments
            mkConfig(
                fragmentDefinitionWeight = 1.0,
                fragmentSpreadWeight = CompoundingWeight.Once
            ).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val spreads = doc.allChildrenOfType<FragmentSpread>()
                    val defs = doc.allChildrenOfType<FragmentDefinition>()
                    spreads.size == defs.size
                }
            }
        }

    @Test
    fun FragmentSpreadWeight(): Unit =
        runBlocking {
            // disabled
            mkConfig(fragmentSpreadWeight = CompoundingWeight.Never).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<FragmentSpread>().isEmpty()
                }
            }

            // enabled
            mkConfig(fragmentSpreadWeight = CompoundingWeight.Once).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<FragmentSpread>().isNotEmpty()
                }
            }
        }

    @Test
    fun ImplicitNullValueWeight(): Unit =
        runBlocking {
            val schema = "type Query { x(a:Int, b:Int!=0, c:Int=0):Int }".asSchema

            // disabled
            mkConfig(
                fieldSelectionWeight = CompoundingWeight.Once,
                implicitNullValueWeight = 0.0
            ).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val fields = doc
                        .allChildrenOfType<Field>()
                        .filter { it.name == "x" }
                    assume(fields.isNotEmpty())
                    fields.all { it.arguments.size == 3 }
                }
            }

            // enabled
            mkConfig(
                fieldSelectionWeight = CompoundingWeight.Once,
                implicitNullValueWeight = 1.0
            ).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val fields = doc.allChildrenOfType<Field>()
                    fields.all { it.arguments.isEmpty() }
                }
            }
        }

    @Test
    fun InlineFragmentWeight(): Unit =
        runBlocking {
            // disabled
            mkConfig(inlineFragmentWeight = CompoundingWeight.Never).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<InlineFragment>().isEmpty()
                }
            }

            // enabled
            mkConfig(
                fieldSelectionWeight = CompoundingWeight.Never,
                inlineFragmentWeight = CompoundingWeight.Once
            ).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    doc.allChildrenOfType<InlineFragment>().isNotEmpty()
                }
            }
        }

    @Test
    fun OperationCount(): Unit =
        runBlocking {
            arbitrary {
                val operationCount = Arb.int(1..10).bind()
                val cfg = mkConfig(operationCount = operationCount)
                val doc = Arb.graphQLDocument(schema, cfg).bind()
                operationCount to doc
            }.forAll { (operationCount, doc) ->
                doc.allChildrenOfType<OperationDefinition>().size == operationCount
            }
        }

    @Test
    fun UntypedInlineFragmentWeight(): Unit =
        runBlocking {
            val schema = "type Query { x:Int }".asSchema

            // disabled
            mkConfig(inlineFragmentWeight = CompoundingWeight.Once, untypedInlineFragmentWeight = 0.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val inlineFragments = doc.allChildrenOfType<InlineFragment>()
                    assume(inlineFragments.isNotEmpty())
                    inlineFragments.all { it.typeCondition != null }
                }
            }

            // enabled
            mkConfig(inlineFragmentWeight = CompoundingWeight.Once, untypedInlineFragmentWeight = 1.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val inlineFragments = doc.allChildrenOfType<InlineFragment>()
                    assume(inlineFragments.isNotEmpty())
                    inlineFragments.all { it.typeCondition == null }
                }
            }
        }

    @Test
    fun VariableWeight(): Unit =
        runBlocking {
            val schema = "type Query { x(a:Int!):Int }".asSchema

            // disabled
            mkConfig(variableWeight = 0.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    val defs = doc.allChildrenOfType<VariableDefinition>()
                    val refs = doc.allChildrenOfType<VariableReference>()
                    defs.isEmpty() && refs.isEmpty()
                }
            }

            // enabled
            mkConfig(variableWeight = 1.0).let { cfg ->
                Arb.graphQLDocument(schema, cfg).forAll { doc ->
                    // skip over any generations don't select the 'x' field
                    assume(doc.allChildrenOfType<Field>().any { it.name == "x" })

                    val defs = doc.allChildrenOfType<VariableDefinition>()
                    val refs = doc.allChildrenOfType<VariableReference>()
                    defs.isNotEmpty() && refs.isNotEmpty()
                }
            }
        }

    @Test
    fun `VariableWeight -- variable reuse stress test`() {
        // Multi-operation documents can have variable reconciliation problems.
        // For example, when building operation B, we may spread a fragment that was originally
        // generated for use in operation A. If that fragment makes use of variables, then those
        // variables need to be reconciled with any existing variables for operation B.
        val cfg = Config(
            OperationCount(2..2),
            FragmentSpreadWeight(CompoundingWeight(.4, 1)),
            VariableWeight(.3)
        )
        assertAllDocumentsValid("type Query { x(a:Int!):Int }", cfg)
    }

    @Test
    fun `VariableWeight -- no variables-in-directives-on-variables`(): Unit =
        runBlocking {
            // VariableDefinitions may have directives, though those directives may not use variables.
            // A validation hole in graphql-java allows documents with this pattern to pass validation
            // and be executed even though they are not valid according to the spec:
            //    https://github.com/graphql-java/graphql-java/issues/3927
            //
            // This test ensures that we are not generating this pattern
            val cfg = Config(
                AppliedDirectiveWeight(CompoundingWeight.Once),
                VariableWeight(1.0)
            )
            val schema = "type Query { x(a:Int!):Int }".asSchema

            Arb
                .graphQLDocument(schema, cfg)
                .forAll { doc ->
                    val badVariableRefs = doc
                        .allChildrenOfType<VariableDefinition>()
                        .flatMap { it.allChildrenOfType<Directive>() }
                        .flatMap { it.allChildrenOfType<VariableReference>() }

                    badVariableRefs.isEmpty()
                }
        }

    @Test
    fun `VariableWeight -- nullable variables are not used in non-nullable positions`(): Unit =
        runBlocking {
            val cfg = mkConfig(variableWeight = 1.0)
            val schema = "type Query { x(a:Int, b:Int!=0):Int }".asSchema
            Arb.graphQLDocument(schema, cfg).forAll { doc ->
                val bargs = doc.allChildrenOfType<Argument>().filter { it.name == "b" }
                assume(bargs.isNotEmpty())

                val vdefs = doc.allChildrenOfType<VariableDefinition>().associateBy { it.name }
                bargs
                    .flatMap { it.allChildrenOfType<VariableReference>() }
                    .map { vdefs[it.name]!!.type }
                    .all { it is NonNullType }
            }
        }

    private fun assertAllDocumentsValid(
        sdl: String,
        cfg: Config = Config.default
    ) = assertAllDocumentsValid(sdl.asSchema, cfg)

    private fun assertAllDocumentsValid(
        schema: GraphQLSchema,
        cfg: Config = Config.default,
        iter: Int = iterations
    ): Unit =
        runBlocking {
            Arb.graphQLDocument(schema, cfg).assertAllValid(schema, iter)
        }

    private fun Arb<Document>.assertAllValid(
        schema: GraphQLSchema,
        iter: Int = iterations
    ): Unit =
        runBlocking {
            minInvalid(schema, iter)?.let { doc ->
                val errors = validate(schema, doc)
                debug(schema, doc, errors)
                fail(
                    buildString {
                        append("Testing failed with seed: $seed\n")
                        append("Document is not valid:\n")
                        errors.forEach { append(" - $it\n") }
                        append("Document:\n")
                        append(AstPrinter.printAst(doc))
                        append("\n")
                    }
                )
            }
        }

    private fun Arb<Document>.minInvalid(
        schema: GraphQLSchema,
        iter: Int = iterations
    ): Document? {
        return withCheck { assertTrue(validate(schema, it).isEmpty()) }
            .minViolation(DocumentComparator, randomSource, iter)
            ?.value
    }
}

private fun validate(
    schema: GraphQLSchema,
    doc: Document
): List<ValidationError> = ParseAndValidate.validate(schema, doc)

// Tests in this suite can be tricky to debug. This method is useful for inspecting the state of a
// tests input in a non-suspending context
@Suppress("UNUSED", "UNUSED_PARAMETER", "UNUSED_VARIABLE")
private fun debug(
    schema: GraphQLSchema,
    doc: Document,
    vararg extras: Any,
) {
    val rels = TypeRelations(schema)
    val docString = AstPrinter.printAst(doc)
    println(docString)
    val operations = doc.getDefinitionsOfType(OperationDefinition::class.java)
    val children = doc.children
    val allChildren = doc.allChildren

    // set a breakpoint on this assignment:
    val x = 1
}
