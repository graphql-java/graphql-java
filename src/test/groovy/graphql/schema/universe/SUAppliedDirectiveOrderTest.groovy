package graphql.schema.universe

import graphql.AssertException
import graphql.TestUtil
import graphql.schema.idl.SchemaParser
import spock.lang.Specification

class SUAppliedDirectiveOrderTest extends Specification {

    def "native builders retain applied directive order across transformations"() {
        given:
        def universe = new SchemaUniverse()
        def a = universe.newAppliedDirective("a")
        def m = universe.newAppliedDirective("m")
        def firstZ = universe.newAppliedDirective("z")
        def secondZ = universe.newAppliedDirective("z")
        def b = universe.newAppliedDirective("b")
        def query = universe.newObjectType("Query")
        def field = universe.newField("value")
        def string = universe.newScalarType("String")
        def base = universe.newSchema("base")
                .queryType(query)
                .addField(query, field)
                .setFieldType(field, string)
                .addAppliedDirective(query, firstZ)
                .addAppliedDirective(query, a)
                .addAppliedDirective(query, secondZ)
                .addAppliedDirective(query, m)
                .addSchemaAppliedDirective(m)
                .addSchemaAppliedDirective(a)
                .build()

        expect:
        base.getAppliedDirectives(query) == [firstZ, a, secondZ, m]
        base.getAppliedDirectives(query, "z") == [firstZ, secondZ]
        base.getChild(query, SUEdgeKind.APPLIED_DIRECTIVE, "z").is(firstZ)
        base.containsEdge(query, SUEdgeKind.APPLIED_DIRECTIVE, secondZ)
        base.schemaAppliedDirectives == [m, a]

        when:
        def removed = base.transform("removed", builder ->
                builder.removeAppliedDirective(query, a))
        def appended = removed.transform("appended", builder ->
                builder.addAppliedDirective(query, a)
                        .addSchemaAppliedDirective(firstZ))
        def replaced = base.transform("replaced", builder ->
                builder.replaceAppliedDirective(query, secondZ, b)
                        .replaceSchemaAppliedDirective(m, firstZ))
        def reordered = base.transform("reordered", builder ->
                builder.replaceAppliedDirectives(query, [m, secondZ, a, firstZ])
                        .replaceSchemaAppliedDirectives([a, m]))

        then:
        removed.getAppliedDirectives(query) == [firstZ, secondZ, m]
        appended.getAppliedDirectives(query) == [firstZ, secondZ, m, a]
        appended.schemaAppliedDirectives == [m, a, firstZ]
        replaced.getAppliedDirectives(query) == [firstZ, a, b, m]
        replaced.schemaAppliedDirectives == [firstZ, a]
        reordered.getAppliedDirectives(query) == [m, secondZ, a, firstZ]
        reordered.schemaAppliedDirectives == [a, m]

        and:
        base.getAppliedDirectives(query) == [firstZ, a, secondZ, m]
        base.schemaAppliedDirectives == [m, a]
        base.sharesOutgoingEdgesWith(removed, field)

        when:
        base.transform("invalid", builder ->
                builder.replaceAppliedDirective(query, b, a))

        then:
        thrown(AssertException)
    }

    def "SDL and GraphQLSchema round trips retain order on every directive container"() {
        given:
        def registry = new SchemaParser().parse(directivesEverywhereSdl())
        def graphQLSchema = TestUtil.schema(directivesEverywhereSdl())

        when:
        def nativeSchema = importWithKnownNameOrder("native", registry)
        def importedSchema = importWithKnownNameOrder("graphql", graphQLSchema)
        def exportedSchema = importWithKnownNameOrder(
                "exported",
                importedSchema.toGraphQLSchema())

        then:
        directiveOrders(nativeSchema).values().every { it == expectedOrder() }
        directiveOrders(importedSchema).values().every { it == expectedOrder() }
        directiveOrders(exportedSchema).values().every { it == expectedOrder() }
    }

    private static SUSchema importWithKnownNameOrder(String name, Object source) {
        def universe = new SchemaUniverse()
        universe.newAppliedDirective("a")
        universe.newAppliedDirective("m")
        universe.newAppliedDirective("z")
        if (source instanceof graphql.schema.GraphQLSchema) {
            return universe.importSchema(name, (graphql.schema.GraphQLSchema) source)
        }
        return universe.importSchema(
                name,
                (graphql.schema.idl.TypeDefinitionRegistry) source)
    }

    private static Map<String, List<String>> directiveOrders(SUSchema schema) {
        def query = schema.queryType
        def objectField = schema.getField(query, "id")
        def objectArgument = schema.getArgument(objectField, "format")
        def node = schema.getInterfaceType("Node")
        def interfaceField = schema.getField(node, "id")
        def interfaceArgument = schema.getArgument(interfaceField, "format")
        def mode = schema.getEnumType("Mode")
        def filter = schema.getInputObjectType("Filter")
        return [
                schema           : schema.schemaAppliedDirectives*.name,
                scalar           : schema.getAppliedDirectives(
                        schema.getScalarType("Custom"))*.name,
                object           : schema.getAppliedDirectives(query)*.name,
                objectField      : schema.getAppliedDirectives(objectField)*.name,
                objectArgument   : schema.getAppliedDirectives(objectArgument)*.name,
                interfaceType    : schema.getAppliedDirectives(node)*.name,
                interfaceField   : schema.getAppliedDirectives(interfaceField)*.name,
                interfaceArgument: schema.getAppliedDirectives(interfaceArgument)*.name,
                unionType        : schema.getAppliedDirectives(
                        schema.getUnionType("Result"))*.name,
                enumType         : schema.getAppliedDirectives(mode)*.name,
                enumValue        : schema.getAppliedDirectives(
                        schema.getEnumValue(mode, "ONE"))*.name,
                inputObject      : schema.getAppliedDirectives(filter)*.name,
                inputField       : schema.getAppliedDirectives(
                        schema.getInputField(filter, "value"))*.name
        ]
    }

    private static List<String> expectedOrder() {
        return ["z", "a", "z", "m"]
    }

    private static String directivesEverywhereSdl() {
        return '''
            directive @a on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE |
              INPUT_OBJECT | INPUT_FIELD_DEFINITION
            directive @m on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE |
              INPUT_OBJECT | INPUT_FIELD_DEFINITION
            directive @z repeatable on SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM | ENUM_VALUE |
              INPUT_OBJECT | INPUT_FIELD_DEFINITION

            schema @z @a @z @m {
              query: Query
            }

            scalar Custom @z @a @z @m

            interface Node @z @a @z @m {
              id(format: String @z @a @z @m): ID @z @a @z @m
            }

            type Query implements Node @z @a @z @m {
              id(format: String @z @a @z @m): ID @z @a @z @m
              result(filter: Filter): Result
              custom: Custom
              mode: Mode
            }

            union Result @z @a @z @m = Query

            enum Mode @z @a @z @m {
              ONE @z @a @z @m
            }

            input Filter @z @a @z @m {
              value: String @z @a @z @m
            }
        '''
    }
}
