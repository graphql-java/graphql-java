@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

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
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class GraphQLDocumentGenTest : FunSpec({
    test("generates valid documents for a trivial schema with minimal config") {
        assertAllDocumentsValid(schema("type Query { x: Int }"), minimalDocumentConfig)
    }

    test("generates valid documents for a trivial schema with default config") {
        assertAllDocumentsValid(schema("type Query { x: Int }"), Config.default)
    }

    test("generates valid documents for a representative schema with minimal config") {
        assertAllDocumentsValid(documentTestSchema, minimalDocumentConfig)
    }

    test("generates valid documents for a representative schema with default config") {
        assertAllDocumentsValid(documentTestSchema, Config.default)
    }

    test("generates valid documents for schemas with default values") {
        val schema = schema(
            """
                directive @dir(inp: Inp = {x: 2}) repeatable on QUERY | FIELD
                input Inp { x: Int = 1, y: Int! = 2, z: Int = null }
                type Query { x(inp: Inp = {y: 3, z: 0}): Int }
            """.trimIndent()
        )
        assertAllDocumentsValid(schema, Config.default)
    }

    test("generates valid documents for arbitrary generated schemas") {
        val config = Config.default +
            (SchemaSize to 20) +
            (FragmentSpreadWeight to CompoundingWeight(.2, 1)) +
            (InlineFragmentWeight to CompoundingWeight(.2, 1))
        val cases = Arb.graphQLSchema(config).flatMap { schema ->
            Arb.graphQLDocument(schema, config).map { document -> schema to document }
        }
        checkAll(PropTestConfig(iterations = 500), cases) { (schema, document) ->
            assertValid(schema, document)
        }
    }

    test("handles merged selections, unions, and recursive types") {
        val schemas = listOf(
            """
                type Obj { x: Int, next: Obj, others: [Obj] }
                type Query { obj: Obj }
            """.trimIndent(),
            """
                interface I { i: I }
                type Obj implements I { x: Int, i: I }
                type Query { i: I }
            """.trimIndent(),
            """
                type Foo { x: Int, u: U }
                type Bar { y: Int, u: U }
                union U = Foo | Bar
                type Query { u: U }
            """.trimIndent(),
            """
                input Input { x: Int, input: Input }
                type Query { x(input: Input!): Int }
            """.trimIndent(),
            """
                type Obj { obj: Obj! }
                type Query { obj: Obj! }
            """.trimIndent()
        )
        schemas.forEach { sdl -> assertAllDocumentsValid(schema(sdl), Config.default, 100) }
    }

    test("generates valid subscription documents") {
        val schema = schema(
            """
                type Obj { x: Int, child: Obj }
                type Subscription { x: Int, obj: Obj }
                type Query { x: Int }
            """.trimIndent()
        )
        assertAllDocumentsValid(schema, Config.default)
    }

    test("AliasWeight controls aliases") {
        val schema = schema("type Query { x: Int }")
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, minimalDocumentConfig + (AliasWeight to 0.0))) { document ->
            document.allChildrenOfType<Field>().all { it.alias == null }.shouldBe(true)
        }
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, minimalDocumentConfig + (AliasWeight to 1.0))) { document ->
            document.allChildrenOfType<Field>().all { it.alias != null }.shouldBe(true)
        }
    }

    test("FieldNameLength controls generated alias names") {
        val schema = schema("type Query { x: Int }")
        val cases = Arb.int(1..10).flatMap { length ->
            val config = minimalDocumentConfig +
                (AliasWeight to 1.0) +
                (FieldNameLength to length..length)
            Arb.graphQLDocument(schema, config).map { length to it }
        }
        checkAll(documentPropertyConfig, cases) { (length, document) ->
            document.allChildrenOfType<Field>()
                .filter { it.alias != "x" && it.alias != "__typename" }
                .all { it.alias?.length == length }
                .shouldBe(true)
        }
    }

    test("AnonymousOperationWeight controls anonymous operations") {
        val schema = schema("type Query { x: Int }")
        val named = minimalDocumentConfig + (AnonymousOperationWeight to 0.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, named)) { document ->
            document.getDefinitionsOfType(OperationDefinition::class.java).all { it.name != null }.shouldBe(true)
        }

        val anonymous = minimalDocumentConfig + (AnonymousOperationWeight to 1.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, anonymous)) { document ->
            val operations = document.getDefinitionsOfType(OperationDefinition::class.java)
            operations.size.shouldBe(1)
            operations.single().name.shouldBe(null)
        }
    }

    test("BanSelectionCoordinates excludes configured fields") {
        val schema = schema("type Query { x: Int, y: Int }")
        val config = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "x"))
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, config)) { document ->
            document.allChildrenOfType<Field>().none { it.name == "x" }.shouldBe(true)
        }
    }

    test("AppliedDirectiveWeight and BanDirectiveNames control document directives") {
        val schema = schema(
            """
                directive @dir(arg: Int) repeatable on QUERY | FIELD
                type Query { x: Int }
            """.trimIndent()
        )
        val enabled = minimalDocumentConfig +
            (AppliedDirectiveWeight to CompoundingWeight.Once) +
            (BanDirectiveNames to builtinDirectives.keys)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, enabled)) { document ->
            document.allChildrenOfType<Directive>().filter { it.name == "dir" }.shouldNotBeEmpty()
        }

        val disabled = enabled + (AppliedDirectiveWeight to CompoundingWeight.Never)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, disabled)) { document ->
            document.allChildrenOfType<Directive>().shouldBeEmpty()
        }

        val banned = enabled + (BanDirectiveNames to (builtinDirectives.keys + "dir"))
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, banned)) { document ->
            document.allChildrenOfType<Directive>().none { it.name == "dir" }.shouldBe(true)
        }
    }

    test("DocumentUncoercedValueWeight controls coercible literals") {
        val schema = schema("type Query { field(arg: Float!): Int }")
        val base = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "__typename"))
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (DocumentUncoercedValueWeight to 0.0))) { document ->
            document.allChildrenOfType<Argument>().all { it.value is FloatValue }.shouldBe(true)
        }
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (DocumentUncoercedValueWeight to 1.0))) { document ->
            document.allChildrenOfType<Argument>().all { it.value is IntValue }.shouldBe(true)
        }
    }

    test("fragment configuration generates valid named and inline fragments") {
        checkAll(documentPropertyConfig, Arb.graphQLDocument(documentTestSchema, minimalDocumentConfig)) { document ->
            document.allChildrenOfType<FragmentSpread>().shouldBeEmpty()
            document.allChildrenOfType<InlineFragment>().shouldBeEmpty()
        }

        val namedConfig = minimalDocumentConfig +
            (FieldSelectionWeight to CompoundingWeight.Never) +
            (FragmentSpreadWeight to CompoundingWeight.Once) +
            (FragmentDefinitionWeight to 1.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(documentTestSchema, namedConfig)) { document ->
            val spreads = document.allChildrenOfType<FragmentSpread>()
            val definitions = document.getDefinitionsOfType(FragmentDefinition::class.java)
            spreads.shouldNotBeEmpty()
            definitions.shouldNotBeEmpty()
            spreads.size.shouldBe(definitions.size)
            assertValid(documentTestSchema, document)
        }

        val inlineConfig = minimalDocumentConfig +
            (FieldSelectionWeight to CompoundingWeight.Never) +
            (InlineFragmentWeight to CompoundingWeight.Once)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(documentTestSchema, inlineConfig)) { document ->
            document.allChildrenOfType<InlineFragment>().shouldNotBeEmpty()
            assertValid(documentTestSchema, document)
        }
    }

    test("UntypedInlineFragmentWeight controls type conditions") {
        val schema = schema("type Query { x: Int }")
        val base = minimalDocumentConfig +
            (FieldSelectionWeight to CompoundingWeight.Never) +
            (InlineFragmentWeight to CompoundingWeight.Once)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (UntypedInlineFragmentWeight to 0.0))) { document ->
            document.allChildrenOfType<InlineFragment>().all { it.typeCondition != null }.shouldBe(true)
        }
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (UntypedInlineFragmentWeight to 1.0))) { document ->
            document.allChildrenOfType<InlineFragment>().all { it.typeCondition == null }.shouldBe(true)
        }
    }

    test("ImplicitNullValueWeight controls optional arguments") {
        val schema = schema("type Query { x(a: Int, b: Int! = 0, c: Int = 0): Int }")
        val base = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "__typename"))
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (ImplicitNullValueWeight to 0.0))) { document ->
            document.allChildrenOfType<Field>().filter { it.name == "x" }.all { it.arguments.size == 3 }.shouldBe(true)
        }
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, base + (ImplicitNullValueWeight to 1.0))) { document ->
            document.allChildrenOfType<Field>().filter { it.name == "x" }.all { it.arguments.isEmpty() }.shouldBe(true)
        }
    }

    test("OperationCount controls operation definitions") {
        val cases = Arb.int(1..5).flatMap { count ->
            val config = minimalDocumentConfig +
                (AnonymousOperationWeight to 0.0) +
                (OperationCount to count..count)
            Arb.graphQLDocument(documentTestSchema, config).map { count to it }
        }
        checkAll(documentPropertyConfig, cases) { (count, document) ->
            document.getDefinitionsOfType(OperationDefinition::class.java).size.shouldBe(count)
        }
    }

    test("VariableWeight controls variable generation") {
        val schema = schema("type Query { x(a: Int!): Int }")
        val disabled = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "__typename")) +
            (VariableWeight to 0.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, disabled)) { document ->
            document.allChildrenOfType<VariableDefinition>().shouldBeEmpty()
            document.allChildrenOfType<VariableReference>().shouldBeEmpty()
        }

        val config = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "__typename")) +
            (VariableWeight to 1.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, config)) { document ->
            document.allChildrenOfType<VariableDefinition>().shouldNotBeEmpty()
            document.allChildrenOfType<VariableReference>().shouldNotBeEmpty()
            assertValid(schema, document)
        }
    }

    test("reuses variables safely across operations and fragments") {
        val schema = schema("type Query { x(a: Int!): Int }")
        val config = Config.default +
            (OperationCount to 2..2) +
            (FragmentSpreadWeight to CompoundingWeight(.4, 1)) +
            (VariableWeight to .3)
        assertAllDocumentsValid(schema, config)
    }

    test("directives on variable definitions do not reference variables") {
        val config = Config.default +
            (AppliedDirectiveWeight to CompoundingWeight.Once) +
            (VariableWeight to 1.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(documentTestSchema, config)) { document ->
            document.allChildrenOfType<VariableDefinition>()
                .flatMap { it.allChildrenOfType<Directive>() }
                .flatMap { it.allChildrenOfType<VariableReference>() }
                .shouldBeEmpty()
        }
    }

    test("nullable variables are not used in non-null positions") {
        val schema = schema("type Query { x(a: Int, b: Int! = 0): Int }")
        val config = minimalDocumentConfig +
            (BanSelectionCoordinates to setOf("Query" to "__typename")) +
            (VariableWeight to 1.0)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, config)) { document ->
            val definitions = document.allChildrenOfType<VariableDefinition>().associateBy { it.name }
            document.allChildrenOfType<Argument>()
                .filter { it.name == "b" }
                .flatMap { it.allChildrenOfType<VariableReference>() }
                .all { definitions.getValue(it.name).type is NonNullType }
                .shouldBe(true)
        }
    }

    test("incremental directives are excluded from subscription operations and mutation roots") {
        val schema = schema(
            """
                directive @defer(if: Boolean = true, label: String) on FRAGMENT_SPREAD | INLINE_FRAGMENT
                directive @stream(if: Boolean = true, label: String, initialCount: Int = 0) on FIELD
                type Obj { x: Int, child: Obj }
                type Mutation { x: Int, obj: Obj }
                type Subscription { x: Int, obj: Obj }
                type Query { x: Int }
            """.trimIndent()
        )
        val config = minimalDocumentConfig +
            (AppliedDirectiveWeight to CompoundingWeight.Once) +
            (OperationCount to 3..3)
        checkAll(documentPropertyConfig, Arb.graphQLDocument(schema, config)) { document ->
            document.getDefinitionsOfType(OperationDefinition::class.java)
                .filter { it.operation == OperationDefinition.Operation.SUBSCRIPTION }
                .all { operation ->
                    operation.allChildrenOfType<Directive>().none { it.name == "defer" || it.name == "stream" }
                }
                .shouldBe(true)

            document.getDefinitionsOfType(OperationDefinition::class.java)
                .filter { it.operation == OperationDefinition.Operation.MUTATION }
                .flatMap { it.selectionSet.selections }
                .filterIsInstance<DirectivesContainer<*>>()
                .all { selection -> selection.directives.none { it.name == "defer" || it.name == "stream" } }
                .shouldBe(true)
        }
    }
})

private val documentPropertyConfig = PropTestConfig(iterations = 100)

private val minimalDocumentConfig = Config.default +
    (AliasWeight to 0.0) +
    (AnonymousOperationWeight to 0.0) +
    (AppliedDirectiveWeight to CompoundingWeight.Never) +
    (DocumentUncoercedValueWeight to 0.0) +
    (ExplicitNullValueWeight to 0.0) +
    (FieldNameLength to 4..4) +
    (FieldSelectionWeight to CompoundingWeight.Once) +
    (FragmentDefinitionWeight to 0.0) +
    (FragmentSpreadWeight to CompoundingWeight.Never) +
    (ImplicitNullValueWeight to 0.0) +
    (InlineFragmentWeight to CompoundingWeight.Never) +
    (MaxSelectionSetDepth to 1) +
    (OperationCount to 1..1) +
    (TypeNameLength to 4..4) +
    (UntypedInlineFragmentWeight to 0.0) +
    (VariableWeight to 0.0)

private val documentTestSchema = schema(
    """
        directive @dir(arg: Int!) repeatable on QUERY | MUTATION | SUBSCRIPTION | FIELD | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT | VARIABLE_DEFINITION
        input Input { a: Int, b: [Int!]!, c: [[[Int]]] }
        type Foo implements I1 { x: Int, next: Union, i1(input: Input!): I1! }
        type Bar implements I1 & I2 { x: Int, y: Int, next: Union }
        union Union = Foo | Bar
        interface I1 { x: Int }
        interface I2 { y: Int }
        type Query { x(a: Int!): Int, union: Union, y(input: Input!): Union }
        type Mutation { x: Int, y: Int }
        type Subscription { x: Int, y: Int }
    """.trimIndent()
)

private fun schema(sdl: String): GraphQLSchema =
    FastSchemaGenerator().makeExecutableSchema(
        SchemaParser().parse(sdl),
        RuntimeWiring.MOCKED_WIRING
    )

private suspend fun assertAllDocumentsValid(
    schema: GraphQLSchema,
    config: Config,
    iterations: Int = 250
) {
    checkAll(PropTestConfig(iterations = iterations), Arb.graphQLDocument(schema, config)) { document ->
        assertValid(schema, document)
    }
}

private fun assertValid(
    schema: GraphQLSchema,
    document: Document
) {
    withClue(AstPrinter.printAst(document)) {
        ParseAndValidate.validate(schema, document).shouldBeEmpty()
    }
}
