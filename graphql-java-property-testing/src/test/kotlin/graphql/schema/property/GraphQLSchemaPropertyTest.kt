@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package graphql.schema.property

import graphql.Directives
import graphql.Scalars
import graphql.introspection.Introspection
import graphql.language.StringValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.Value
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLTypeVisitorStub
import graphql.schema.SchemaTraverser
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.validation.SchemaValidator
import graphql.util.TraversalControl
import graphql.util.TraverserContext
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.checkAll
import io.kotest.property.arbitrary.long

class GraphQLSchemaPropertyTest : FunSpec({
    test("generated schemas are valid") {
        checkAll(PropTestConfig(iterations = 100), Arb.generatedGraphQLSchema()) { generated ->
            withClue(generated.sdl) {
                SchemaValidator().validateSchema(generated.schema).shouldBeEmpty()
            }
        }
    }

    test("generated schemas round trip through SDL") {
        checkAll(PropTestConfig(iterations = 100), Arb.generatedGraphQLSchema()) { generated ->
            withClue(generated.sdl) {
                val registry = SchemaParser().parse(generated.sdl)
                val rebuilt = FastSchemaGenerator()
                    .makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING)
                SchemaValidator().validateSchema(rebuilt).shouldBeEmpty()
                SchemaPrinter().print(rebuilt).shouldBe(generated.sdl)
            }
        }
    }

    test("default values are generated and remain valid") {
        val config = Config.default + (DefaultValueWeight to 1.0)
        checkAll(PropTestConfig(iterations = 50), Arb.generatedGraphQLSchema(config)) { generated ->
            withClue(generated.sdl) {
                val collector = DefaultValueCollector()
                SchemaTraverser().depthFirstFullSchema(collector, generated.schema)
                collector.values.shouldNotBeEmpty()
                collector.values.all { it }.shouldBe(true)
                SchemaValidator().validateSchema(generated.schema).shouldBeEmpty()
            }
        }
    }

    test("oneOf input fields never receive defaults") {
        val config = Config.default +
            (OneOfTypeWeight to 1.0) +
            (DefaultValueWeight to 1.0) +
            (TypeTypeWeights to TypeTypeWeights.zero + (TypeType.Input to 1.0))

        checkAll(PropTestConfig(iterations = 50), Arb.generatedGraphQLSchema(config)) { generated ->
            withClue(generated.sdl) {
                generated.schema.allTypesAsList
                    .filterIsInstance<GraphQLInputObjectType>()
                    .filter { it.isOneOf }
                    .flatMap { it.fields }
                    .all { !it.hasSetDefaultValue() }
                    .shouldBe(true)
            }
        }
    }

    test("cyclic nullable inputs receive finite non-trivial defaults") {
        val seedSchema = parseTestSchema(
            "input Recursive { next: Recursive } type Query { x(value: Recursive): Int }"
        )
        val recursive = seedSchema.getType("Recursive") as GraphQLInputObjectType
        val types = GraphQLTypes.empty.copy(inputs = mapOf(recursive.name to recursive))
        val config = Config.default +
            (DefaultValueWeight to 1.0) +
            (ExplicitNullValueWeight to 0.0) +
            (MaxValueDepth to 3)
        checkAll(PropTestConfig(iterations = 50), Arb.graphQLSchema(types, config)) { schema ->
            val input = schema.getType("Recursive") as GraphQLInputObjectType
            val defaultValue = input.getFieldDefinition("next").inputFieldDefaultValue.value as ObjectValue
            defaultValue.objectFields.shouldNotBeEmpty()
            defaultValue.objectFields.none { it.value is NullValue }.shouldBe(true)
        }
    }

    test("applied directives are generated without invalid dependency cycles") {
        val testDirective = GraphQLDirective.newDirective()
            .name("testDirective")
            .argument(
                GraphQLArgument.newArgument()
                    .name("arg")
                    .type(Scalars.GraphQLInt)
            )
            .validLocations(*Introspection.DirectiveLocation.entries.toTypedArray())
            .build()
        val config = Config.default +
            (IncludeTypes to (GraphQLTypes.empty + testDirective)) +
            (AppliedDirectiveWeight to CompoundingWeight.Once) +
            (BanDirectiveNames to builtinDirectives.keys) +
            (DefaultValueWeight to 0.0)

        val disabled = config + (AppliedDirectiveWeight to CompoundingWeight.Never)
        checkAll(PropTestConfig(iterations = 20), Arb.graphQLSchema(disabled)) { schema ->
            val collector = AppliedDirectiveCollector()
            SchemaTraverser().depthFirstFullSchema(collector, schema)
            collector.directives.none { it.name == testDirective.name }.shouldBe(true)
        }

        checkAll(PropTestConfig(iterations = 50), Arb.generatedGraphQLSchema(config)) { generated ->
            withClue(generated.sdl) {
                val collector = AppliedDirectiveCollector()
                SchemaTraverser().depthFirstFullSchema(collector, generated.schema)
                collector.directives.filter { it.name == testDirective.name }.shouldNotBeEmpty()
                SchemaValidator().validateSchema(generated.schema).shouldBeEmpty()
            }
        }
    }

    test("applied directives are not added to introspection elements") {
        val config = Config.default +
            (AppliedDirectiveWeight to CompoundingWeight.Once) +
            (DefaultValueWeight to 0.0)

        checkAll(PropTestConfig(iterations = 20), Arb.graphQLSchema(config)) { schema ->
            val collector = AppliedDirectiveCollector()
            listOf(
                schema.introspectionSchemaType,
                schema.introspectionSchemaFieldDefinition,
                schema.introspectionTypenameFieldDefinition,
                schema.introspectionTypeFieldDefinition
            ).forEach { SchemaTraverser().depthFirst(collector, it) }
            collector.directives.shouldBeEmpty()
        }
    }

    test("generated input types contain no mandatory cycles") {
        val config = Config.default +
            (NonNullableTypeWeight to 1.0) +
            (ListTypeWeight to CompoundingWeight.Never) +
            (OneOfTypeWeight to 0.0) +
            (DefaultValueWeight to 0.0)

        checkAll(PropTestConfig(iterations = 100), Arb.graphQLSchema(config)) { schema ->
            CycleGroups.mandatoryInputCycles(schema).isEmpty().shouldBe(true)
        }
    }

    test("uncoerced defaults exercise GraphQL input coercion") {
        val config = Config.default +
            (DefaultValueWeight to 1.0) +
            (SchemaUncoercedValueWeight to 1.0)
        checkAll(PropTestConfig(iterations = 25), Arb.generatedGraphQLSchema(config)) { generated ->
            withClue(generated.sdl) {
                SchemaValidator().validateSchema(generated.schema).shouldBeEmpty()
            }
        }
    }

    test("single-value coercion cannot null a non-null list") {
        val listType = GraphQLNonNull.nonNull(GraphQLList.list(Scalars.GraphQLString)) as GraphQLInputType
        val config = Config.default + (ExplicitNullValueWeight to 1.0)
        checkAll(Arb.long()) { seed ->
            val generator = GraphQLInputValueGenerator(
                schema = minimalSchema(),
                config = config,
                randomSource = RandomSource.seeded(seed),
                uncoercedValueWeight = 1.0
            )
            generator.generate(listType).shouldBeInstanceOf<StringValue>()
        }
    }

    test("single-value coercion preserves nested list boundaries") {
        val innerList = GraphQLNonNull.nonNull(GraphQLList.list(Scalars.GraphQLString))
        val listType = GraphQLNonNull.nonNull(GraphQLList.list(innerList)) as GraphQLInputType
        val config = Config.default +
            (ExplicitNullValueWeight to 1.0) +
            (ListValueSize to 3..3)
        checkAll(Arb.long()) { seed ->
            val generator = GraphQLInputValueGenerator(
                schema = minimalSchema(),
                config = config,
                randomSource = RandomSource.seeded(seed),
                uncoercedValueWeight = 1.0
            )
            schemaWithDefault(listType, generator.generate(listType))
        }
    }
})

private fun minimalSchema(): GraphQLSchema =
    GraphQLSchema.newSchema()
        .query(
            GraphQLObjectType.newObject()
                .name("Query")
                .field(
                    GraphQLFieldDefinition.newFieldDefinition()
                        .name("field")
                        .type(Scalars.GraphQLString)
                )
        )
        .build()

private fun schemaWithDefault(
    type: GraphQLInputType,
    value: Value<*>
): GraphQLSchema =
    GraphQLSchema.newSchema()
        .query(
            GraphQLObjectType.newObject()
                .name("Query")
                .field(
                    GraphQLFieldDefinition.newFieldDefinition()
                        .name("field")
                        .type(Scalars.GraphQLString)
                        .argument(
                            GraphQLArgument.newArgument()
                                .name("argument")
                                .type(type)
                                .defaultValueLiteral(value)
                        )
                )
        )
        .build()

private class AppliedDirectiveCollector : GraphQLTypeVisitorStub() {
    val directives = mutableListOf<GraphQLAppliedDirective>()

    override fun visitGraphQLAppliedDirective(
        node: GraphQLAppliedDirective,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl {
        directives += node
        return TraversalControl.CONTINUE
    }
}

private class DefaultValueCollector : GraphQLTypeVisitorStub() {
    val values = mutableListOf<Boolean>()

    override fun visitGraphQLArgument(
        node: GraphQLArgument,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl {
        val parent = context.parentNode
        if (parent is GraphQLDirective && parent.name in builtinDirectives) return TraversalControl.CONTINUE
        values += node.hasSetDefaultValue()
        return TraversalControl.CONTINUE
    }

    override fun visitGraphQLInputObjectField(
        node: graphql.schema.GraphQLInputObjectField,
        context: TraverserContext<GraphQLSchemaElement>
    ): TraversalControl {
        val parent = context.parentNode
        if (parent !is GraphQLInputObjectType || !parent.isOneOf) {
            values += node.hasSetDefaultValue()
        }
        return TraversalControl.CONTINUE
    }
}
