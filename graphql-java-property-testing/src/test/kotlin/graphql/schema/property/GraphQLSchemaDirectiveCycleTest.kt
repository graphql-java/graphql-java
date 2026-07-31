package graphql.schema.property

import graphql.Scalars
import graphql.introspection.Introspection.DirectiveLocation
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLTypeReference
import graphql.schema.SchemaTransformer
import graphql.schema.idl.SchemaPrinter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.RandomSource

class GraphQLSchemaDirectiveCycleTest : FunSpec({
    test("direct dependencies between directive arguments are one-way") {
        val schema = directiveControlSchema(
            directives = listOf(
                controlDirective("a", Scalars.GraphQLInt, DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", Scalars.GraphQLInt, DirectiveLocation.ARGUMENT_DEFINITION)
            )
        ).withArbitraryAppliedDirectives()

        val aHasB = schema.directiveArgumentDirectives("a").contains("b")
        val bHasA = schema.directiveArgumentDirectives("b").contains("a")
        (aHasB xor bHasA).shouldBe(true)
        SchemaPrinter().print(schema)
    }

    test("dependencies through directive argument input fields are one-way") {
        val input = GraphQLInputObjectType.newInputObject()
            .name("InputA")
            .field(
                GraphQLInputObjectField.newInputObjectField()
                    .name("field")
                    .type(GraphQLNonNull.nonNull(Scalars.GraphQLInt))
            )
            .build()
        val schema = directiveControlSchema(
            directives = listOf(
                controlDirective("a", GraphQLTypeReference.typeRef("InputA"), DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", Scalars.GraphQLInt, DirectiveLocation.INPUT_FIELD_DEFINITION)
            ),
            inputs = listOf(input)
        ).withArbitraryAppliedDirectives()

        val fieldHasB = (schema.getType("InputA") as GraphQLInputObjectType)
            .getFieldDefinition("field")
            .appliedDirectives
            .any { it.name == "b" }
        val bHasA = schema.directiveArgumentDirectives("b").contains("a")
        (fieldHasB xor bHasA).shouldBe(true)
        SchemaPrinter().print(schema)
    }

    test("dependencies through directive argument enum values are one-way") {
        val enum = GraphQLEnumType.newEnum().name("E").value("X").build()
        val schema = directiveControlSchema(
            directives = listOf(
                controlDirective("a", GraphQLTypeReference.typeRef("E"), DirectiveLocation.ARGUMENT_DEFINITION),
                controlDirective("b", Scalars.GraphQLInt, DirectiveLocation.ENUM_VALUE)
            ),
            enums = listOf(enum)
        ).withArbitraryAppliedDirectives()

        val valueHasB = requireNotNull((schema.getType("E") as GraphQLEnumType).getValue("X"))
            .appliedDirectives
            .any { it.name == "b" }
        val bHasA = schema.directiveArgumentDirectives("b").contains("a")
        (valueHasB xor bHasA).shouldBe(true)
        SchemaPrinter().print(schema)
    }
})

private fun GraphQLSchema.withArbitraryAppliedDirectives(): GraphQLSchema {
    val config = Config.default +
        (AppliedDirectiveWeight to CompoundingWeight.Always) +
        (DirectiveIsRepeatable to 0.0) +
        (DefaultValueWeight to 0.0)
    val randomSource = RandomSource.seeded(0)
    val generator = GraphQLInputValueGenerator(
        schema = this,
        config = config,
        randomSource = randomSource,
        uncoercedValueWeight = 0.0,
        allEdgesGraph = CycleGroups.allInputCycles(this),
        mandatoryEdgesGraph = CycleGroups.mandatoryInputCycles(this)
    )
    return SchemaTransformer.transformSchema(
        this,
        AddAppliedDirectives(this, generator, config, randomSource)
    )
}

private fun GraphQLSchema.directiveArgumentDirectives(directiveName: String): Set<String> {
    val directive = requireNotNull(getDirective(directiveName))
    val argument = requireNotNull(directive.getArgument("arg"))
    return argument.appliedDirectives.mapTo(linkedSetOf()) { it.name }
}

private fun directiveControlSchema(
    directives: List<GraphQLDirective>,
    inputs: List<GraphQLInputObjectType> = emptyList(),
    enums: List<GraphQLEnumType> = emptyList()
): GraphQLSchema {
    val query = GraphQLObjectType.newObject()
        .name("Query")
        .field(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("placeholder")
                .type(Scalars.GraphQLInt)
                .argument(GraphQLArgument.newArgument().name("arg").type(Scalars.GraphQLInt))
        )
        .build()
    val types = GraphQLTypes.empty.copy(
        directives = directives.associateBy { it.name },
        objects = mapOf(query.name to query),
        inputs = inputs.associateBy { it.name },
        enums = enums.associateBy { it.name }
    )
    val config = Config.default +
        (AppliedDirectiveWeight to CompoundingWeight.Never) +
        (DefaultValueWeight to 0.0)
    return SchemaGenerator(config, RandomSource.seeded(0)).createSchema(types)
}

private fun controlDirective(
    name: String,
    argumentType: GraphQLInputType,
    vararg locations: DirectiveLocation
): GraphQLDirective =
    GraphQLDirective.newDirective()
        .name(name)
        .argument(GraphQLArgument.newArgument().name("arg").type(argumentType))
        .validLocations(*locations)
        .build()
