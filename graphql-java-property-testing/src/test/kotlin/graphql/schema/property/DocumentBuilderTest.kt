package graphql.schema.property

import graphql.Scalars.GraphQLInt
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.AstPrinter
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DocumentBuilderTest : FunSpec({
    test("ArgumentKey compares the printed value instead of AST identity") {
        val first = Argument("arg", NullValue.of())
        val second = Argument("arg", NullValue.of())
        first.shouldNotBe(second)
        ArgumentKey(first).shouldBe(ArgumentKey(second))
        ArgumentKey(first).hashCode().shouldBe(ArgumentKey(second).hashCode())
        ArgumentKey(first).arg.shouldBe(first)
    }

    test("FieldKey equality includes arguments and wrapper structure") {
        DocumentBuilderFixture("type Query { x(arg: Int): Int }").run {
            val first = FieldKey("x", null, setOf(ArgumentKey(Argument("arg", IntValue.of(1)))), typeExpr("Int"))
            val same = FieldKey("x", null, setOf(ArgumentKey(Argument("arg", IntValue.of(1)))), typeExpr("Int"))
            val different = FieldKey("x", null, setOf(ArgumentKey(Argument("arg", NullValue.of()))), typeExpr("Int"))
            first.shouldBe(same)
            first.shouldNotBe(different)
        }
    }

    test("TypeExpr hashes list and null wrappers in order") {
        TypeExpr(GraphQLInt).listNullHash.shouldBe(1)
        TypeExpr(GraphQLList.list(GraphQLInt)).listNullHash.shouldBe(2)
        TypeExpr(GraphQLNonNull.nonNull(GraphQLInt)).listNullHash.shouldBe(3)
        TypeExpr(GraphQLList.list(GraphQLList.list(GraphQLInt))).listNullHash.shouldBe(4)
        TypeExpr(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLInt))).listNullHash.shouldBe(5)
        TypeExpr(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLInt))).listNullHash.shouldBe(6)
        TypeExpr(GraphQLList.list(GraphQLNonNull.nonNull(GraphQLInt)))
            .shouldNotBe(TypeExpr(GraphQLNonNull.nonNull(GraphQLList.list(GraphQLInt))))
    }

    test("KeyTree creates scalar leaves and composite branches") {
        DocumentBuilderFixture("type Query { x: Int, q: Query }").run {
            val scalarKey = key("Query", "x")
            val objectKey = key("Query", "q")
            KeyTree(scalarKey)[scalarKey].shouldBe(null)
            (KeyTree(objectKey)[objectKey] != null).shouldBe(true)
        }
    }

    test("KeyTree detects merge-compatible and conflicting response keys") {
        DocumentBuilderFixture("type Query { x: Int, q: Query }").run {
            val x = key("Query", "x")
            val q = key("Query", "q")
            KeyTree().canMerge(KeyTree()).shouldBe(true)
            KeyTree(x).canMerge(KeyTree(x)).shouldBe(true)
            KeyTree(x).canMerge(KeyTree(q)).shouldBe(true)
            KeyTree(x).canMerge(KeyTree(key("Query", "q", "x"))).shouldBe(false)

            val first = KeyTree(q).apply { merge(q, KeyTree(x)) }
            val compatible = KeyTree(q).apply { merge(q, KeyTree(key("Query", "__typename"))) }
            val conflicting = KeyTree(q).apply { merge(q, KeyTree(key("Query", "q", "x"))) }
            first.canMerge(compatible).shouldBe(true)
            first.canMerge(conflicting).shouldBe(false)
        }
    }

    test("KeyTree merge recursively combines compatible branches") {
        DocumentBuilderFixture("type Query { x: Int, q: Query }").run {
            val x = key("Query", "x")
            val q = key("Query", "q")
            val typename = key("Query", "__typename")
            KeyTree(x).merge(KeyTree(typename)).toMap().shouldBe(mapOf(x to null, typename to null))

            val first = KeyTree(q).apply { merge(q, KeyTree(x)) }
            val second = KeyTree(q).apply { merge(q, KeyTree(typename)) }
            first.merge(second).toMap().shouldBe(mapOf(q to mapOf(x to null, typename to null)))
        }
    }

    test("KeyTree clone is independent") {
        DocumentBuilderFixture("type Query { x: Int, y: Int }").run {
            val x = key("Query", "x")
            val y = key("Query", "y")
            val original = KeyTree(x)
            val clone = original.clone()
            original.toMap().shouldBe(clone.toMap())
            original.merge(KeyTree(y))
            original.toMap().shouldNotBe(clone.toMap())
        }
    }

    test("SelectionsBuilder emits scalar and aliased fields") {
        DocumentBuilderFixture("type Query { x: Int }").run {
            selections.add(FieldSelection(key("Query", "x")))
            assertDocument("{ x }")
        }
        DocumentBuilderFixture("type Query { x: Int }").run {
            selections.add(FieldSelection(key("Query", "x", "alias")))
            assertDocument("{ alias: x }")
        }
    }

    test("SelectionsBuilder emits nested object selections") {
        DocumentBuilderFixture("type Obj { y: Int } type Query { obj: Obj }").run {
            val objectKey = key("Query", "obj")
            val scope = selections.newFieldScope(objectKey)
            scope.add(FieldSelection(key("Obj", "y")))
            selections.add(FieldSelection(objectKey, scope))
            assertDocument("{ obj { y } }")
        }
    }

    test("SelectionsBuilder emits typed and untyped inline fragments") {
        DocumentBuilderFixture("type Query { x: Int }").run {
            selections.add(InlineFragmentSelection(null, listOf(FieldSelection(key("Query", "x")))))
            assertDocument("{ ... { x } }")
        }
        DocumentBuilderFixture("type Query { x: Int }").run {
            selections.add(InlineFragmentSelection("Query", listOf(FieldSelection(key("Query", "x")))))
            assertDocument("{ ... on Query { x } }")
        }
    }

    test("DocumentBuilder emits reusable fragment definitions") {
        DocumentBuilderFixture("type Query { x: Int }").run {
            val scope = selections.newSpreadScope()
            scope.add(FieldSelection(key("Query", "x")))
            fragments += FragmentDef("Frag", schema.queryType, scope, emptyList(), emptyList())
            selections.add(FragmentSpreadSelection("Frag", scope.selections))
            selections.add(FragmentSpreadSelection("Frag", scope.selections))
            selections.add(FieldSelection(key("Query", "x")))
            assertDocument("{ ... Frag ... Frag x } fragment Frag on Query { x }")
        }
    }

    test("SelectionsBuilder checks aliases and arguments before adding fields") {
        DocumentBuilderFixture("type Query { x(arg: Int): Int }").run {
            val x = field("Query", "x").key()
            selections.canAdd(x).shouldBe(true)
            selections.add(FieldSelection(x))
            selections.canAdd(x.copy()).shouldBe(true)
            selections.canAdd(x.copy(alias = "alias")).shouldBe(true)
            val withNull = setOf(ArgumentKey(Argument("arg", NullValue.of())))
            selections.canAdd(x.copy(arguments = withNull)).shouldBe(false)
            selections.canAdd(x.copy(alias = "alias", arguments = withNull)).shouldBe(true)
        }
    }

    test("field scopes share merge constraints without losing local selections") {
        DocumentBuilderFixture("type Obj { x: Int, y: Int, z: Int } type Query { obj: Obj }").run {
            val objectKey = key("Query", "obj")
            val firstScope = SelectionsBuilder(listOf(FieldSelection(key("Obj", "x"))))
            selections.add(FieldSelection(objectKey, firstScope))

            val secondScope = selections.newFieldScope(objectKey)
            secondScope.canAdd(key("Obj", "x", "__typename")).shouldBe(true)
            secondScope.canAdd(key("Obj", "__typename", "x")).shouldBe(false)
            secondScope.add(FieldSelection(key("Obj", "y")))
            selections.add(FieldSelection(objectKey, secondScope))

            val thirdScope = selections.newFieldScope(objectKey)
            thirdScope.canAdd(key("Obj", "__typename", "x")).shouldBe(false)
            thirdScope.canAdd(key("Obj", "__typename", "y")).shouldBe(false)
            thirdScope.add(FieldSelection(key("Obj", "z")))
            selections.add(FieldSelection(objectKey, thirdScope))
            assertDocument("{ obj { x } obj { y } obj { z } }")
        }
    }
})

private class DocumentBuilderFixture(sdl: String) {
    val schema = parseTestSchema(sdl)
    private val schemas = Schemas(schema)
    val fragments = Fragments(schemas)
    val selections = SelectionsBuilder()

    fun field(type: String, fieldName: String): GraphQLFieldDefinition {
        if (fieldName == Introspection.TypeNameMetaFieldDef.name) return Introspection.TypeNameMetaFieldDef
        return requireNotNull(schema.getFieldDefinition(FieldCoordinates.coordinates(type, fieldName)))
    }

    fun key(
        type: String,
        fieldName: String,
        alias: String? = null,
        vararg arguments: Argument
    ): FieldKey = field(type, fieldName).key(alias, arguments.toSet())

    fun typeExpr(name: String): TypeExpr = TypeExpr(requireNotNull(schema.getType(name)) as graphql.schema.GraphQLOutputType)

    fun assertDocument(expected: String) {
        val builder = DocumentBuilder(schemas, fragments)
        builder.add(
            OperationDefinition.newOperationDefinition()
                .operation(OperationDefinition.Operation.QUERY)
                .selectionSet(selections.build())
                .build()
        )
        val expectedDocument = Parser().parseDocument(expected)
        AstPrinter.printAst(builder.build()).shouldBe(AstPrinter.printAst(expectedDocument))
    }
}
