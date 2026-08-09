package graphql.arbitrary

import graphql.Scalars.GraphQLInt
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.AstPrinter
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.OperationDefinition
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentBuilderTest {
    private class Fixture(
        sdl: String,
        fn: Fixture.() -> Unit = {}
    ) {
        val schema = sdl.asSchema
        val query = schema.queryType
        val schemas = Schemas(schema)
        val fragments = Fragments(schemas)
        val sb = SelectionsBuilder()
        val __typename = Introspection.TypeNameMetaFieldDef

        init {
            fn(this)
        }

        fun field(
            type: String,
            fieldName: String
        ): GraphQLFieldDefinition =
            if (fieldName == __typename.name) {
                __typename
            } else {
                requireNotNull(schema.getFieldDefinition(FieldCoordinates.coordinates(type, fieldName)))
            }

        fun key(
            type: String,
            fieldName: String,
            alias: String? = null,
            vararg arguments: Argument
        ): FieldKey = field(type, fieldName).key(alias, arguments.toSet())

        val String.compositeType: GraphQLCompositeType get() = requireNotNull(schema.getTypeAs(this))
        val String.outputType: GraphQLOutputType get() = requireNotNull(schema.getTypeAs(this))
        val String.scalarType: GraphQLScalarType get() = requireNotNull(schema.getTypeAs(this))
        val String.typeExpr: TypeExpr get() = TypeExpr(requireNotNull(schema.getTypeAs<GraphQLOutputType>(this)))

        fun assertDocument(expected: String) {
            val db = DocumentBuilder(schemas, fragments)
            val op = OperationDefinition
                .newOperationDefinition()
                .operation(OperationDefinition.Operation.QUERY)
                .selectionSet(sb.build())
                .build()
            db.add(op)

            val doc = db.build()

            val normExpected = AstPrinter.printAst(expected.asDocument)
            val actual = AstPrinter.printAst(doc)
            assertEquals(normExpected, actual)
        }
    }

    @Test
    fun `ArgumentKey -- equals and hashCode`() {
        val a1 = Argument("arg", NullValue.of())
        val a2 = Argument("arg", NullValue.of())
        // sanity
        assertNotEquals(a1, a2)

        val ak1 = ArgumentKey(a1)
        val ak2 = ArgumentKey(a2)
        assertEquals(ak1, ak2)
        assertEquals(ak1.hashCode(), ak2.hashCode())

        assertEquals(a1, ak1.arg)
    }

    @Test
    fun `FieldKey -- equality`() {
        Fixture("type Query { x(arg:Int): Int }") {
            val fk1 = FieldKey(
                fieldName = "x",
                alias = null,
                arguments = setOf(
                    ArgumentKey(Argument("arg", IntValue.of(1)))
                ),
                fieldType = "Int".typeExpr
            )

            val fk2 = FieldKey(
                fieldName = "x",
                alias = null,
                arguments = setOf(
                    ArgumentKey(Argument("arg", IntValue.of(1)))
                ),
                fieldType = "Int".typeExpr
            )

            assertEquals(fk1, fk2)

            val fk3 = FieldKey(
                fieldName = "x",
                alias = null,
                arguments = setOf(
                    ArgumentKey(Argument("arg", NullValue.of()))
                ),
                fieldType = "Int".typeExpr
            )
            assertNotEquals(fk1, fk3)
        }
    }

    @Test
    fun `TypeExpr -- hash`() {
        // Int
        assertEquals(1, TypeExpr(GraphQLInt).listNullHash)

        // [Int]
        assertEquals(2, TypeExpr(GraphQLList(GraphQLInt)).listNullHash)

        // Int!
        assertEquals(3, TypeExpr(GraphQLNonNull(GraphQLInt)).listNullHash)

        // [[Int]]
        assertEquals(
            4,
            TypeExpr(
                GraphQLList(GraphQLList(GraphQLInt))
            ).listNullHash
        )

        // [Int!]
        assertEquals(
            5,
            TypeExpr(
                GraphQLList(GraphQLNonNull(GraphQLInt))
            ).listNullHash
        )

        // [Int]!
        assertEquals(
            6,
            TypeExpr(
                GraphQLNonNull(GraphQLList(GraphQLInt))
            ).listNullHash
        )
    }

    @Test
    fun `TypeExpr -- equality`() {
        // no wrappers
        assertEquals(
            TypeExpr(GraphQLInt),
            TypeExpr(GraphQLInt),
        )

        // with wrappers
        assertEquals(
            TypeExpr(GraphQLList(GraphQLNonNull(GraphQLInt))),
            TypeExpr(GraphQLList(GraphQLNonNull(GraphQLInt))),
        )

        // wrappers in diff order are not equal
        assertNotEquals(
            TypeExpr(GraphQLNonNull(GraphQLList(GraphQLInt))),
            TypeExpr(GraphQLList(GraphQLNonNull(GraphQLInt))),
        )
    }

    @Test
    fun `KeyTree -- create from field key`() {
        Fixture("type Query { x:Int, q:Query }") {
            val xk = key("Query", "x")
            val xkt = KeyTree(xk)
            assertNull(xkt[xk])

            val qk = key("Query", "q")
            val qkt = KeyTree(qk)
            assertNotNull(qkt[qk])
        }
    }

    @Test
    fun `KeyTree -- canMerge`() {
        Fixture("type Query { x:Int, q:Query }") {
            val xk = key("Query", "x")
            val qk = key("Query", "q")
            val tk = key("Query", "__typename")

            // empty-empty
            assertTrue(KeyTree().canMerge(KeyTree()))

            // same selections
            assertTrue(
                KeyTree(xk).canMerge(KeyTree(xk))
            )

            // different selections
            assertTrue(
                KeyTree(xk).canMerge(KeyTree(qk))
            )

            // conflicting selections
            assertFalse(
                KeyTree(xk).canMerge(
                    KeyTree(key("Query", "q", "x"))
                )
            )

            // compatible nested selections
            let {
                val a = KeyTree(qk).apply {
                    merge(qk, KeyTree(xk))
                }
                val b = KeyTree(qk).apply {
                    merge(qk, KeyTree(tk))
                }
                assertTrue(a.canMerge(b))
            }

            // incompatible nested selections
            let {
                val a = KeyTree(qk).apply {
                    merge(qk, KeyTree(xk))
                }
                val b = KeyTree(qk).apply {
                    merge(qk, KeyTree(key("Query", "q", "x")))
                }
                assertFalse(a.canMerge(b))
            }
        }
    }

    @Test
    fun `KeyTree -- merge`() {
        Fixture("type Query { x:Int, q:Query }") {
            val xk = key("Query", "x")
            val qk = key("Query", "q")
            val tk = key("Query", "__typename")

            // empty
            assertEquals(
                emptyMap<FieldKey, Any?>(),
                KeyTree().merge(KeyTree()).toMap()
            )

            // same selections
            assertEquals(
                mapOf(xk to null),
                KeyTree(xk).merge(KeyTree(xk)).toMap()
            )

            // compatible selections
            assertEquals(
                mapOf(xk to null, tk to null),
                KeyTree(xk).merge(KeyTree(tk)).toMap()
            )

            // nested selections
            let {
                val a = KeyTree(qk).also { tree -> tree.merge(qk, KeyTree(xk)) }
                val b = KeyTree(qk).also { tree -> tree.merge(qk, KeyTree(tk)) }

                val result = a.merge(b).toMap()
                assertEquals(
                    mapOf(
                        qk to mapOf(xk to null, tk to null)
                    ),
                    result
                )
            }
        }
    }

    @Test
    fun `KeyTree -- clone`() {
        Fixture("type Query { x:Int y:Int}") {
            val xk = key("Query", "x")
            val yk = key("Query", "y")

            val t1 = KeyTree(xk)
            val t2 = t1.clone()
            assertEquals(t1.toMap(), t2.toMap())

            t1.merge(KeyTree(yk))
            assertNotEquals(t1.toMap(), t2.toMap())
        }
    }

    @Test
    fun `SelectionsBuilder -- add scalar field`() {
        Fixture("type Query { x:Int }") {
            sb.add(FieldSelection(key("Query", "x")))
            assertDocument("{x}")
        }
    }

    @Test
    fun `SelectionsBuilder -- add aliased scalar`() {
        Fixture("type Query { x:Int }") {
            val xKey = field("Query", "x").key("alias")
            sb.add(FieldSelection(xKey))
            assertDocument("{alias: x}")
        }
    }

    @Test
    fun `SelectionsBuilder -- add object field`() {
        Fixture(
            """
                type Obj { y:Int }
                type Query { obj:Obj }
            """.trimIndent()
        ) {
            val objk = key("Query", "obj")
            val yk = key("Obj", "y")
            val objs = sb.newFieldScope(objk)
            objs.add(FieldSelection(yk))
            sb.add(FieldSelection(objk, objs))

            assertDocument("{ obj { y } }")
        }
    }

    @Test
    fun `SelectionsBuilder -- add untyped inline fragment`() {
        Fixture("type Query { x:Int }") {
            sb.add(
                InlineFragmentSelection(
                    null,
                    listOf(
                        FieldSelection(key("Query", "x"))
                    )
                )
            )
            assertDocument("{ ... { x } }")
        }
    }

    @Test
    fun `SelectionsBuilder -- add typed inline fragment`() {
        Fixture("type Query { x:Int }") {
            sb.add(
                InlineFragmentSelection(
                    "Query",
                    listOf(
                        FieldSelection(key("Query", "x"))
                    )
                )
            )
            assertDocument("{ ... on Query { x } }")
        }
    }

    @Test
    fun `SelectionsBuilder -- add fragment spread`() {
        Fixture("type Query { x:Int }") {
            val defScope = sb.newSpreadScope()
            defScope.add(FieldSelection(key("Query", "x")))
            val frag = FragmentDef(
                "Frag",
                query,
                defScope,
                emptyList(),
                emptyList()
            )
            fragments += frag
            FragmentSpreadSelection("Frag", defScope.selections)

            sb.add(FragmentSpreadSelection("Frag", defScope.selections))
            sb.add(FragmentSpreadSelection("Frag", defScope.selections))
            sb.add(FieldSelection(key("Query", "x")))

            assertDocument(
                """
                    {
                        ... Frag
                        ... Frag
                        x
                    }
                    fragment Frag on Query { x }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `SelectionsBuilder -- canAdd key`() {
        Fixture("type Query { x(arg:Int):Int }") {
            val xk = field("Query", "x").key()
            assertTrue(sb.canAdd(xk))
            sb.add(FieldSelection(xk))

            // can add key with same key values
            assertTrue(sb.canAdd(xk.copy()))

            // can add key with an unused alias
            assertTrue(sb.canAdd(xk.copy(alias = "alias")))

            // cannot add key with different arguments
            assertFalse(sb.canAdd(xk.copy(arguments = setOf(ArgumentKey(Argument("arg", NullValue.of()))))))

            // can add key with an unused alias and different arguments
            assertTrue(sb.canAdd(xk.copy(alias = "alias", arguments = setOf(ArgumentKey(Argument("arg", NullValue.of()))))))
        }
    }

    @Test
    fun `SelectionsBuilder -- canAdd fieldScope`() {
        Fixture(
            """
                type Obj { x:Int, y:Int, z:Int }
                type Query { obj:Obj }
            """.trimIndent()
        ) {
            // construct a selection set like:
            //    {
            //      obj { x }
            //      obj { y }
            //      obj { z }
            //    }
            val objk = key("Query", "obj")
            sb.add(
                FieldSelection(
                    objk,
                    SelectionsBuilder(
                        listOf(
                            FieldSelection(key("Obj", "x"), null)
                        )
                    )
                )
            )

            assertDocument("{ obj { x } }")

            sb.newFieldScope(objk).also { obj2 ->
                // can add `x`, which is mergeable with selection x above
                assertTrue(obj2.canAdd(key("Obj", "x", "__typename")))
                // cannot add `x:__typename`, which would conflict with selection above
                assertFalse(obj2.canAdd(key("Obj", "__typename", "x")))
                obj2.add(FieldSelection(key("Obj", "y")))

                FieldSelection(objk, obj2).also {
                    assertTrue(sb.canAdd(it))
                    sb.add(it)
                }
            }

            assertDocument(
                """
                    {
                        obj { x }
                        obj { y }
                    }
                """.trimIndent()
            )

            sb.newFieldScope(objk).also { obj3 ->
                // cannot add `x:__typename`, which would conflict with selection above
                assertFalse(obj3.canAdd(key("Obj", "__typename", "x")))
                assertFalse(obj3.canAdd(key("Obj", "__typename", "y")))
                obj3.add(FieldSelection(key("Obj", "z")))

                FieldSelection(objk, obj3).also {
                    assertTrue(sb.canAdd(it))
                    sb.add(it)
                }
            }

            assertDocument(
                """
                    {
                        obj { x }
                        obj { y }
                        obj { z }
                    }
                """.trimIndent()
            )
        }
    }
}
