@file:Suppress("ForbiddenImport")

package graphql.arbitrary

import graphql.Scalars
import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLInt
import graphql.introspection.Introspection
import graphql.language.ArrayValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.Value
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.InputValueWithState
import graphql.schema.SchemaTransformer
import graphql.schema.SchemaTraverser
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GraphQLSchemasTest : ArbPropertyBase() {
    // It's useful to have a directive that can be applied to any element
    private val testDirective = GraphQLDirective
        .newDirective()
        .name("testDirective")
        .repeatable(false)
        .validLocations(*Introspection.DirectiveLocation.values())
        .build()

    @Nested
    inner class SimpleGenSuite : DeepArbSuite<GraphQLSchema>() {
        override val checkedArb = Arb.graphQLSchema().withCheck {}
        override val comparator: Comparator<GraphQLSchema> = GraphQLSchemaComparator
    }

    @Test
    fun `Arb-graphQLSchema can generate an empty-ish schema`(): Unit =
        runBlocking {
            val cfg = Config(SchemaSize(0))
            Arb.graphQLSchema(cfg).checkAll {
                markSuccess()
            }
        }

    @Test
    fun `Arb-graphQLSchema can generate a schema from an empty GraphQLTypes`(): Unit =
        runBlocking {
            Arb.graphQLSchema(GraphQLTypes.empty).forAll {
                true
            }
        }

    @Test
    fun `schema document can be roundtripped through sdl`(): Unit =
        runBlocking {
            Arb.graphQLSchema().forAll(100) { schema ->
                val sdl = SchemaPrinter().print(schema)
                SchemaParser().parse(sdl)
                true
            }
        }

    @Test
    fun `adds default values`(): Unit =
        runBlocking {
            // disabled
            Arb.graphQLSchema(Config(DefaultValueWeight(0.0)))
                .forAll(100) { schema ->
                    val defaultables = CollectDefaultables(includeOneOfFields = true)
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .defaultables

                    defaultables.none { it.isSet }
                }

            // enabled
            Arb.graphQLSchema(Config(DefaultValueWeight(1.0)))
                .forAll(100) { schema ->
                    val defaultables = CollectDefaultables(includeOneOfFields = false)
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .defaultables
                    defaultables.all { it.isSet }
                }
        }

    @Test
    fun `adds default values -- SchemaUncoercedValueWeight`(): Unit =
        runBlocking {
            val cfg = Config(
                DefaultValueWeight(1.0),
                SchemaUncoercedValueWeight(1.0)
            )

            // int for float
            run {
                val query = GraphQLObjectType.newObject()
                    .name("Query")
                    .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                            .name("field")
                            .argument(
                                GraphQLArgument.newArgument().name("arg")
                                    .type(GraphQLNonNull.nonNull(GraphQLFloat))
                            )
                            .type(GraphQLInt)
                    )
                    .build()

                Arb.graphQLSchema(GraphQLTypes.empty.copy(objects = mapOf("Query" to query)), cfg)
                    .forAll { schema ->
                        val field = requireNotNull(schema.getFieldDefinition(FieldCoordinates.coordinates("Query", "field")))
                        val arg = field.getArgument("arg")
                        val default = arg.argumentDefaultValue
                        default.isSet && default.value is IntValue
                    }
            }

            // singleton for list
            run {
                val query = GraphQLObjectType.newObject()
                    .name("Query")
                    .field(
                        GraphQLFieldDefinition.newFieldDefinition()
                            .name("field")
                            .argument(
                                GraphQLArgument.newArgument().name("arg")
                                    .type(
                                        GraphQLNonNull.nonNull(
                                            GraphQLList(
                                                GraphQLNonNull.nonNull(GraphQLInt)
                                            )
                                        )
                                    )
                            )
                            .type(GraphQLInt)
                    )
                    .build()

                Arb.graphQLSchema(GraphQLTypes.empty.copy(objects = mapOf("Query" to query)), cfg)
                    .forAll { schema ->
                        val field = requireNotNull(schema.getFieldDefinition(FieldCoordinates.coordinates("Query", "field")))
                        val arg = field.getArgument("arg")
                        val default = arg.argumentDefaultValue
                        default.isSet && default.value is IntValue
                    }
            }
        }

    @Test
    fun `adds applied directives`(): Unit =
        runBlocking {
            val cfg = Config(IncludeTypes(GraphQLTypes.empty + testDirective))

            // disabled
            Arb.graphQLSchema(cfg + AppliedDirectiveWeight(CompoundingWeight.Never))
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .directives
                    dirs.isEmpty()
                }

            // enabled
            Arb.graphQLSchema(cfg + AppliedDirectiveWeight(CompoundingWeight.Once))
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                        .directives
                    dirs.isNotEmpty()
                }

            // BanDirectiveNames
            Arb.graphQLSchema(
                cfg +
                    AppliedDirectiveWeight(CompoundingWeight.Once) +
                    BanDirectiveNames(setOf(testDirective.name))
            ).forAll(100) { schema ->
                val dirs = CollectAppliedDirectives()
                    .also { SchemaTraverser().depthFirstFullSchema(it, schema) }
                    .directives
                dirs.none { it.name == testDirective.name }
            }
        }

    @Test
    fun `adds applied directives -- but not on introspection elements`(): Unit =
        runBlocking {
            val cfg = Config(
                AppliedDirectiveWeight(CompoundingWeight.Always),
                DirectiveIsRepeatable(0.0),
                IncludeTypes(GraphQLTypes.empty + testDirective)
            )

            Arb.graphQLSchema(cfg)
                .forAll(100) { schema ->
                    val dirs = CollectAppliedDirectives()
                        .also { visitor ->
                            val introspectionRoots = listOf(
                                schema.introspectionSchemaType,
                                schema.introspectionSchemaFieldDefinition,
                                schema.introspectionTypenameFieldDefinition,
                                schema.introspectionTypeFieldDefinition,
                            )
                            introspectionRoots.forEach { elt ->
                                SchemaTraverser().depthFirst(visitor, elt)
                            }
                        }
                        .directives
                    dirs.isEmpty()
                }
        }

    @Test
    fun `AddAppliedDirectives prevents direct cycles between directive arguments`() {
        val transformed = controlSchema(
            directives = listOf(
                controlDirective("a", GraphQLNonNull.nonNull(Scalars.GraphQLInt), Introspection.DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", GraphQLNonNull.nonNull(Scalars.GraphQLInt), Introspection.DirectiveLocation.ARGUMENT_DEFINITION),
            )
        ).transformAppliedDirectives()

        val aArgDirectives = transformed.directiveArgumentAppliedDirectiveNames("a", "arg")
        val bArgDirectives = transformed.directiveArgumentAppliedDirectiveNames("b", "arg")

        assertEquals(true, aArgDirectives.contains("b") xor bArgDirectives.contains("a"))
    }

    @Test
    fun `AddAppliedDirectives prevents cycles through directive argument input fields`() {
        val transformed = controlSchema(
            directives = listOf(
                controlDirective("a", GraphQLTypeReference.typeRef("InputA"), Introspection.DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", GraphQLNonNull.nonNull(Scalars.GraphQLInt), Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION),
            ),
            inputs = listOf(
                GraphQLInputObjectType
                    .newInputObject()
                    .name("InputA")
                    .field(
                        GraphQLInputObjectField
                            .newInputObjectField()
                            .name("field")
                            .type(GraphQLNonNull.nonNull(Scalars.GraphQLInt))
                    ).build()
            )
        ).transformAppliedDirectives()

        val fieldHasB = transformed.inputFieldAppliedDirectiveNames("InputA", "field").contains("b")
        val bArgHasA = transformed.directiveArgumentAppliedDirectiveNames("b", "arg").contains("a")

        assertEquals(true, fieldHasB xor bArgHasA)
    }

    @Test
    fun `AddAppliedDirectives prevents cycles through directive argument enum values`() {
        val transformed = controlSchema(
            directives = listOf(
                controlDirective("a", GraphQLTypeReference.typeRef("E"), Introspection.DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", GraphQLNonNull.nonNull(Scalars.GraphQLInt), Introspection.DirectiveLocation.ENUM_VALUE),
            ),
            enums = listOf(
                GraphQLEnumType
                    .newEnum()
                    .name("E")
                    .value("X")
                    .build()
            )
        ).transformAppliedDirectives()

        val valueHasB = transformed.enumValueAppliedDirectiveNames("E", "X").contains("b")
        val bArgHasA = transformed.directiveArgumentAppliedDirectiveNames("b", "arg").contains("a")

        assertEquals(true, valueHasB xor bArgHasA)
    }

    @Test
    fun `generates inhabited OneOf types`(): Unit =
        runBlocking {
            // an inhabited type is a type for which it is possible to create a finite value
            // https://en.wikipedia.org/wiki/Type_inhabitation
            fun isInhabited(type: GraphQLInputObjectType): Boolean {
                fun loop(
                    seenOneOfs: Set<String>,
                    type: GraphQLInputType
                ): Boolean {
                    assert(type !is GraphQLNonNull)
                    return if (type is GraphQLInputObjectType && type.isOneOf) {
                        val checkableFields = type.fields.filter { f ->
                            if (f.type is GraphQLList) return@filter true
                            val unwrappedFieldType = GraphQLTypeUtil.unwrapAll(f.type)
                            unwrappedFieldType !is GraphQLInputObjectType || unwrappedFieldType.name !in seenOneOfs
                        }
                        checkableFields.any { f ->
                            loop(seenOneOfs + type.name, f.type)
                        }
                    } else {
                        true
                    }
                }

                return loop(emptySet(), type)
            }

            // create a configuration that is likely to produce closed OneOf subgraphs
            // this is characterized by a high generation of OneOf types with small numbers of fields
            val cfg = Config(
                InputObjectTypeSize(1.asIntRange()),
                TypeKindWeights(TypeKindWeights.zero + (TypeKind.Input to 1.0)),
                ExplicitNullValueWeight(0.0),
                OneOfTypeWeight(1.0)
            )

            // without default values
            Arb.graphQLSchema(cfg + DefaultValueWeight(0.0))
                .forAll { schema ->
                    schema.allTypesAsList
                        .mapNotNull { it as? GraphQLInputObjectType }
                        .all(::isInhabited)
                }

            // with default values
            Arb.graphQLSchema(cfg + DefaultValueWeight(1.0))
                .forAll { schema ->
                    schema.allTypesAsList
                        .mapNotNull { it as? GraphQLInputObjectType }
                        .all(::isInhabited)
                }
        }

    @Test
    fun `generated schemas do not have cyclic default values`(): Unit =
        runBlocking {
            // see https://spec.graphql.org/draft/#sec-Input-Object-Default-Value-Has-Cycle
            // returns true if the schema contains a default value that forms a cycle
            fun containsInvalidDefaultValues(schema: GraphQLSchema): Boolean {
                val inputTypes = schema.allTypesAsList.filterIsInstance<GraphQLInputObjectType>()

                // InputObjectDefaultValueHasCycle(inputObject, defaultValue, visitedFields)
                // returns true if this object forms a cycle
                fun objectContainsInvalidDefaultValues(
                    inputObject: GraphQLInputObjectType,
                    defaultValue: Value<*>?,
                    visitedFields: Set<GraphQLInputObjectField>
                ): Boolean {
                    if (defaultValue is ArrayValue) {
                        return defaultValue.values.any { objectContainsInvalidDefaultValues(inputObject, it, visitedFields) }
                    }
                    // An explicit null value terminates the fallthrough chain — no cycle possible.
                    // This is what makes `input A { a: A = null }` valid.
                    if (defaultValue is NullValue) return false
                    val presentFields: Map<String, Value<*>?> = when (defaultValue) {
                        null -> emptyMap() // initial call: treat as empty map
                        is ObjectValue -> defaultValue.objectFields.associate { it.name to it.value }
                        else -> return false // scalar value — no cycle possible
                    }
                    for (field in inputObject.fields) {
                        val namedFieldType =
                            GraphQLTypeUtil.unwrapAll(field.type) as? GraphQLInputObjectType ?: continue
                        if (field.name in presentFields) {
                            // Field explicitly provided in the default value object.
                            // Explicit values (including null) terminate the fallthrough chain,
                            // so we recurse without tracking visited fields.
                            // This is what makes `input A { a: A = { a: null } }` valid.
                            val fieldValue = presentFields[field.name] ?: continue
                            if (objectContainsInvalidDefaultValues(namedFieldType, fieldValue, visitedFields)) {
                                return true
                            }
                        } else {
                            // Field absent from the default value object — fall through to its schema default.
                            // This is where cycles can form: absent fields delegate to their schema default,
                            // which may itself have absent fields that delegate back.
                            if (!field.hasSetDefaultValue()) continue
                            val schemaDefault = field.inputFieldDefaultValue.value as? Value<*> ?: continue
                            if (field in visitedFields) {
                                return true
                            }
                            if (objectContainsInvalidDefaultValues(namedFieldType, schemaDefault, visitedFields + field)) {
                                return true
                            }
                        }
                    }
                    return false
                }

                val invalidInputs = inputTypes.filter { objectContainsInvalidDefaultValues(it, null, emptySet()) }
                return invalidInputs.isNotEmpty()
            }

            Arb.graphQLSchema(
                Config(DefaultValueWeight(1.0))
            ).forAll(100) { schema ->
                !containsInvalidDefaultValues(schema)
            }
        }

    @Test
    fun `generated schemas do not contain hard input cycles`(): Unit =
        runBlocking {
            val names = GraphQLNames(mapOf(TypeKind.Input to setOf("A", "B")))
            val cfg = Config(
                NonNullableTypeWeight(1.0),
                ListTypeWeight(CompoundingWeight.Never),
                OneOfTypeWeight(0.0)
            )

            val arb = arbitrary {
                val types = Arb.graphQLTypes(names, cfg).bind()
                Arb.graphQLSchema(types, cfg).bind()
            }

            arb.forAll { schema ->
                CycleGroups.mandatoryInputCycles(schema).isEmpty()
            }
        }

    @Test
    fun `generated schemas can contain values on cyclic input objects`(): Unit =
        runBlocking {
            /**
             * Ensures that for a type like:
             *    input Inp { inp: Inp }
             * That we can generate interesting default values, like:
             *    input Inp { inp: Inp = { inp: { inp: { inp: null } } } }
             */

            val types = """
            type Query { empty:Int }
            input Inp { inp:Inp }
        """.asSchema.let { schema ->
                GraphQLTypes.empty.copy(
                    inputs = mapOf("Inp" to requireNotNull(schema.getTypeAs<GraphQLInputObjectType>("Inp")))
                )
            }

            Arb.graphQLSchema(
                types,
                Config(
                    ExplicitNullValueWeight(0.0),
                    MaxValueDepth(3),
                    DefaultValueWeight(1.0)
                )
            ).forAll { schema ->
                val default = requireNotNull(schema.getTypeAs<GraphQLInputObjectType>("Inp"))
                    .getField("inp")
                    .inputFieldDefaultValue
                    .value as? ObjectValue

                default != null && default.objectFields.isNotEmpty() && default.objectFields.none { it.value is NullValue }
            }
        }

    /** This test makes no assertions but is useful for debugging. */
    @Test
    @Disabled
    fun `dump 1 schema`(): Unit =
        runBlocking {
            val cfg = Config(DescriptionLength(0..0))
            Arb.graphQLSchema(cfg).checkAll(1) {
                val sdl = SchemaPrinter().print(it)
                println(sdl)
                markSuccess()
            }
        }

    private class CollectAppliedDirectives : GraphQLTypeVisitorStub() {
        var directives = mutableListOf<GraphQLAppliedDirective>()

        override fun visitGraphQLAppliedDirective(
            node: GraphQLAppliedDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            directives += node
            return TraversalControl.CONTINUE
        }
    }

    private class CollectDefaultables(val includeOneOfFields: Boolean) : GraphQLTypeVisitorStub() {
        var defaultables = mutableListOf<InputValueWithState>()

        override fun visitGraphQLArgument(
            node: GraphQLArgument,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            defaultables += node.argumentDefaultValue
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLInputObjectField(
            node: GraphQLInputObjectField,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            val parent = context.parentNode
            if (parent is GraphQLInputObjectType && parent.isOneOf && !includeOneOfFields) {
                return TraversalControl.CONTINUE
            }
            defaultables += node.inputFieldDefaultValue
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLDirective(
            node: GraphQLDirective,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl {
            // skip built-in directives, some of which define arguments with default values that are outside the control of this generator
            if (node.name in builtinDirectives) {
                return TraversalControl.ABORT
            }
            return TraversalControl.CONTINUE
        }

        override fun visitGraphQLType(
            node: GraphQLSchemaElement,
            context: TraverserContext<GraphQLSchemaElement>
        ): TraversalControl =
            // skip introspection fields and types, which are outside the control of this generator
            if (node is GraphQLNamedType && node.name.startsWith("__")) {
                TraversalControl.ABORT
            } else {
                TraversalControl.CONTINUE
            }
    }

    private fun GraphQLSchema.transformAppliedDirectives(
        seed: Long = 0L,
        cfg: Config =
            Config(
                AppliedDirectiveWeight(CompoundingWeight.Always),
                DirectiveIsRepeatable(0.0),
                DefaultValueWeight(0.0)
            )
    ): GraphQLSchema {
        val rs = RandomSource.seeded(seed)
        val valueGenerator = GraphQLInputValueGen(
            schema = this,
            config = cfg,
            randomSource = rs,
            uncoercedValueWeight = cfg[SchemaUncoercedValueWeight],
            allEdgesGraph = CycleGroups.allInputCycles(this),
            mandatoryEdgesGraph = CycleGroups.mandatoryInputCycles(this)
        )
        return SchemaTransformer.transformSchema(
            this,
            AddAppliedDirectives(
                this,
                valueGenerator,
                cfg,
                rs
            )
        )
    }

    private fun GraphQLSchema.directiveArgumentAppliedDirectiveNames(
        directiveName: String,
        argName: String
    ): Set<String> =
        requireNotNull(getDirective(directiveName))
            .let { requireNotNull(it.getArgument(argName)) }
            .appliedDirectives
            .mapTo(mutableSetOf()) { it.name }

    private fun GraphQLSchema.inputFieldAppliedDirectiveNames(
        inputName: String,
        fieldName: String
    ): Set<String> =
        requireNotNull(getTypeAs<GraphQLInputObjectType>(inputName))
            .let { requireNotNull(it.getField(fieldName)) }
            .appliedDirectives
            .mapTo(mutableSetOf()) { it.name }

    private fun GraphQLSchema.enumValueAppliedDirectiveNames(
        enumName: String,
        valueName: String
    ): Set<String> =
        requireNotNull(getTypeAs<graphql.schema.GraphQLEnumType>(enumName))
            .let { requireNotNull(it.getValue(valueName)) }
            .appliedDirectives
            .mapTo(mutableSetOf()) { it.name }

    private fun controlSchema(
        directives: List<GraphQLDirective>,
        inputs: List<GraphQLInputObjectType> = emptyList(),
        enums: List<GraphQLEnumType> = emptyList()
    ): GraphQLSchema {
        val cfg = Config(
            AppliedDirectiveWeight(CompoundingWeight.Never),
            DefaultValueWeight(0.0)
        )
        val query = GraphQLObjectType
            .newObject()
            .name("Query")
            .field(
                GraphQLFieldDefinition
                    .newFieldDefinition()
                    .name("placeholder")
                    .type(Scalars.GraphQLInt)
                    .argument(
                        GraphQLArgument
                            .newArgument()
                            .name("arg")
                            .type(Scalars.GraphQLInt)
                    )
            ).build()

        val types = GraphQLTypes.empty.copy(
            directives = directives.associateBy { it.name },
            objects = mapOf(query.name to query),
            inputs = inputs.associateBy { it.name },
            enums = enums.associateBy { it.name }
        )
        return GraphQLSchemaGen(cfg, RandomSource.seeded(0)).gen(types)
    }

    private fun controlDirective(
        name: String,
        argType: GraphQLInputType,
        vararg locations: Introspection.DirectiveLocation
    ): GraphQLDirective =
        GraphQLDirective
            .newDirective()
            .name(name)
            .argument(
                GraphQLArgument
                    .newArgument()
                    .name("arg")
                    .type(argType)
            ).validLocations(*locations)
            .build()
}
