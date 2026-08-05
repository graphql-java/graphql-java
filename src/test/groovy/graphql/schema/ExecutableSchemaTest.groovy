package graphql.schema

import graphql.AssertException
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.introspection.IntrospectionWithDirectivesSupport
import graphql.schema.universe.SchemaUniverse
import graphql.schema.universe.view.SUSchemaExecutableSchema
import graphql.schema.visibility.BlockedFields
import spock.lang.Specification

class ExecutableSchemaTest extends Specification {

    def "GraphQLSchema and SUSchema expose the same executable type hierarchy"() {
        given:
        ExecutableSchema schema = executableSchema

        expect:
        schema.queryType instanceof SchemaObject
        schema.mutationType.name == "Mutation"
        schema.subscriptionType.name == "Subscription"
        schema.getType("Node") instanceof SchemaInterface
        schema.getType("Search") instanceof SchemaUnion
        schema.getType("Date") instanceof SchemaScalar
        schema.getType("Color") instanceof SchemaEnum
        schema.getType("Choice") instanceof SchemaInputObject
        schema.getType("missing") == null

        and:
        def node = schema.getType("Node") as SchemaFieldsContainer
        schema.getFields(node)*.name == ["id"]
        schema.getField(node, "id").type instanceof SchemaNonNull
        schema.getField(
                schema.getType("Search") as SchemaComposite,
                "missing") == null

        and:
        def colors = schema.getField(schema.queryType, "colors")
        colors.name == "colors"
        colors.type instanceof SchemaNonNull
        colors.type.wrappedType instanceof SchemaList
        colors.type.wrappedType.wrappedType instanceof SchemaNonNull
        colors.type.wrappedType.wrappedType.wrappedType.name == "Color"
        colors.arguments*.name == ["choice"]
        colors.getArgument("choice").type.name == "Choice"
        colors.getArgument("missing") == null

        and:
        def choice = schema.getType("Choice") as SchemaInputObject
        choice.oneOf
        schema.getInputFields(choice)*.name == ["color", "date"]
        schema.getInputField(choice, "color").type.name == "Color"
        schema.getInputField(choice, "color")
                .inputFieldDefaultValue.isNotSet()
        schema.getInputField(choice, "missing") == null
        !(schema.getType("Regular") as SchemaInputObject).oneOf

        and:
        def color = schema.getType("Color") as SchemaEnum
        color.values*.name == ["RED", "GREEN"]
        color.getValue("GREEN").name == "GREEN"
        color.getValue("BLUE") == null

        and:
        def tag = schema.getDirective("tag")
        tag.repeatable
        tag.validLocations().contains(Introspection.DirectiveLocation.FIELD)
        tag.validLocations().contains(Introspection.DirectiveLocation.QUERY)
        tag.arguments*.name == ["value"]
        tag.getArgument("value").argumentDefaultValue.isLiteral()
        tag.getArgument("missing") == null
        schema.getDirective("missing") == null

        and:
        schema.getScalarCoercing(
                schema.getType("Date") as SchemaScalar) != null

        where:
        executableSchema << executableSchemas()
    }

    def "GraphQLSchema and SUSchema expose possible types through the shared contract"() {
        given:
        ExecutableSchema schema = executableSchema
        def node = schema.getType("Node") as SchemaComposite
        def search = schema.getType("Search") as SchemaComposite
        def user = schema.getType("User") as SchemaObject
        def query = schema.queryType

        expect:
        schema.getPossibleTypes(user) == [user]
        schema.getPossibleTypes(node)*.name == ["User"]
        schema.getPossibleTypes(search)*.name == ["User"]
        schema.isPossibleType(user, user)
        schema.isPossibleType(node, user)
        schema.isPossibleType(search, user)
        !schema.isPossibleType(node, query)
        !schema.isPossibleType(search, query)

        where:
        executableSchema << executableSchemas()
    }

    def "GraphQLSchema executable lookups apply output and input field visibility"() {
        given:
        def source = TestUtil.schema('''
            input Filter {
                visible: String
                hidden: String
            }

            type Query {
                visible(filter: Filter): String
                hidden: String
            }
        ''')
        def visibility = BlockedFields.newBlock()
                .addPatterns(["Query.hidden", "Filter.hidden"])
                .build()
        def codeRegistry = source.codeRegistry.transform {
            it.fieldVisibility(visibility)
        }
        ExecutableSchema schema = source.transform {
            it.codeRegistry(codeRegistry)
        }
        def filter = schema.getType("Filter") as SchemaInputObject

        expect:
        schema.getFields(schema.queryType)*.name == ["visible"]
        schema.getField(schema.queryType, "visible") != null
        schema.getField(schema.queryType, "hidden") == null
        schema.getInputFields(filter)*.name == ["visible"]
        schema.getInputField(filter, "visible") != null
        schema.getInputField(filter, "hidden") == null

        and:
        schema.getField(schema.queryType, "__schema") != null
        schema.getField(schema.queryType, "__type") != null
        schema.getField(schema.queryType, "__typename") != null
        schema.mutationType == null
        schema.subscriptionType == null
    }

    def "introspection meta fields use each executable schema's introspection graph"() {
        given:
        def source = new IntrospectionWithDirectivesSupport(
                environment -> true,
                "_custom_")
                .apply(TestUtil.schema("type Query { value: String }"))
        def universeSchema = new SchemaUniverse()
                .importSchema("enhanced", source)
        ExecutableSchema schema = SUSchemaExecutableSchema
                .fromGraphQLSchema(universeSchema, source)

        expect:
        unwrap(schema.getField(schema.queryType, "__schema").type) ==
                schema.introspectionSchemaType
        unwrap(schema.getField(schema.queryType, "__type").type).name ==
                "__Type"
        schema.getField(schema.queryType, "__type")
                .getArgument("name").type instanceof SchemaNonNull
        unwrap(schema.getField(schema.queryType, "__type")
                .getArgument("name").type).name == "String"

        and:
        schema.getField(
                schema.introspectionSchemaType,
                "appliedDirectives") != null
        schema.getType("_custom_AppliedDirective") != null
    }

    def "typename is available on every composite kind"() {
        given:
        ExecutableSchema schema = executableSchema
        def typeField = schema.getField(schema.queryType, "__type")
        def typeNameField = schema.getField(
                schema.getType("Search") as SchemaComposite,
                "__typename")

        expect:
        ["Query", "Node", "Search"].every {
            schema.getField(
                    schema.getType(it) as SchemaComposite,
                    "__typename")?.name == "__typename"
        }
        unwrap(schema.getField(schema.queryType, "__schema").type) ==
                schema.introspectionSchemaType
        unwrap(schema.getField(schema.queryType, "__type").type).name ==
                "__Type"
        typeField.arguments*.name == ["name"]
        typeField.getArgument("name").name == "name"
        typeField.getArgument("name").argumentDefaultValue.isNotSet()
        typeField.getArgument("missing") == null
        typeNameField.arguments.isEmpty()
        typeNameField.type instanceof SchemaNonNull
        unwrap(typeNameField.type).name == "String"

        where:
        executableSchema << executableSchemas()
    }

    def "SUSchema executable views keep scalar coercers outside shared vertices"() {
        given:
        def source = richSchema()
        def universeSchema = new SchemaUniverse()
                .importSchema("schema", source)
        def original = SUSchemaExecutableSchema
                .fromGraphQLSchema(universeSchema, source)
        Coercing<?, ?> replacement = Mock()

        when:
        def changed = original.transform {
            it.scalarCoercing("Date", replacement)
        }

        then:
        original.getScalarCoercing(
                original.getType("Date") as SchemaScalar)
                .is(source.getType("Date").coercing)
        changed.getScalarCoercing(
                changed.getType("Date") as SchemaScalar)
                .is(replacement)

        when:
        def unbound = SUSchemaExecutableSchema
                .newExecutableSchema(universeSchema)
                .build()
        unbound.getScalarCoercing(
                unbound.getType("Date") as SchemaScalar)

        then:
        thrown(AssertException)
    }

    def "SUSchema executable views reject elements from another schema"() {
        given:
        def source = richSchema()
        def firstUniverseSchema = new SchemaUniverse()
                .importSchema("first", source)
        def secondUniverseSchema = new SchemaUniverse()
                .importSchema("second", source)
        def first = SUSchemaExecutableSchema
                .fromGraphQLSchema(firstUniverseSchema, source)
        def second = SUSchemaExecutableSchema
                .fromGraphQLSchema(secondUniverseSchema, source)

        when:
        first.getPossibleTypes(
                second.getType("Node") as SchemaComposite)

        then:
        thrown(AssertException)

        when:
        first.isPossibleType(
                first.getType("Node") as SchemaComposite,
                second.getType("User") as SchemaObject)

        then:
        thrown(AssertException)

        when:
        SUSchemaExecutableSchema.newExecutableSchema(firstUniverseSchema)
                .scalarCoercing(
                        secondUniverseSchema.getScalarType("Date"),
                        source.getType("Date").coercing)

        then:
        thrown(AssertException)
    }

    private static List<ExecutableSchema> executableSchemas() {
        def graphQLSchema = richSchema()
        def universeSchema = new SchemaUniverse()
                .importSchema("schema", graphQLSchema)
        def suSchema = SUSchemaExecutableSchema
                .fromGraphQLSchema(universeSchema, graphQLSchema)
        return [graphQLSchema, suSchema]
    }

    private static GraphQLSchema richSchema() {
        TestUtil.schema('''
            directive @tag(value: String = "fallback") repeatable on FIELD | QUERY

            scalar Date

            enum Color {
                RED
                GREEN
            }

            input Choice @oneOf {
                color: Color
                date: Date
            }

            input Regular {
                value: String
            }

            interface Node {
                id: ID!
            }

            type User implements Node {
                id: ID!
                name: String
            }

            union Search = User

            type Query {
                node(id: ID!): Node
                search: Search
                colors(choice: Choice): [Color!]!
                regular(input: Regular): String
                today: Date
            }

            type Mutation {
                update: User
            }

            type Subscription {
                events: User
            }
        ''')
    }

    private static SchemaType unwrap(SchemaType type) {
        SchemaType current = type
        while (current instanceof SchemaModifiedType) {
            current = (current as SchemaModifiedType).wrappedType
        }
        return current
    }
}
