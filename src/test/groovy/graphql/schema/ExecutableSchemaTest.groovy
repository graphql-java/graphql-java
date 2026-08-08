package graphql.schema

import graphql.AssertException
import graphql.Scalars
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.introspection.IntrospectionWithDirectivesSupport
import graphql.schema.universe.SchemaUniverse
import graphql.schema.universe.view.SUExecutableSchema
import graphql.schema.visibility.BlockedFields
import spock.lang.Specification

class ExecutableSchemaTest extends Specification {

    def "GraphQLSchema and SUSchema expose the same executable type hierarchy"() {
        given:
        ExecutableSchema schema = executableSchema

        expect:
        schema.queryType instanceof SchemaObject
        schema.queryType instanceof SchemaImplementingType
        schema.mutationType.name == "Mutation"
        schema.subscriptionType.name == "Subscription"
        schema.getType("Node") instanceof SchemaInterface
        schema.getType("Node") instanceof SchemaImplementingType
        schema.getType("Search") instanceof SchemaUnion
        schema.getType("Date") instanceof SchemaScalar
        schema.getType("Color") instanceof SchemaEnum
        schema.getType("Choice") instanceof SchemaInputObject
        schema.getType("missing") == null

        and:
        schema.types*.name.containsAll([
                "Query",
                "Mutation",
                "Subscription",
                "Node",
                "Search",
                "User",
                "Date",
                "Color",
                "Choice"
        ])
        schema.directives*.name.contains("tag")
        schema.appliedDirectives.isEmpty()

        and:
        def node = schema.getType("Node") as SchemaInterface
        node.fieldDefinitions*.name == ["id"]
        schema.queryType.fieldDefinitions*.name == [
                "node",
                "search",
                "colors",
                "regular",
                "today"
        ]
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
        choice.fieldDefinitions*.name == ["color", "date"]
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
        def user = schema.getType("User") as SchemaObject
        user.interfaces*.name == ["Node"]
        user.interfaces.every { it instanceof SchemaInterface }
        node.interfaces.isEmpty()
        def search = schema.getType("Search") as SchemaUnion
        search.types*.name == ["User"]
        search.types.every { it instanceof SchemaObject }
        schema.getAppliedDirectives(choice)*.name == ["oneOf"]

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
        def date = schema.getType("Date") as SchemaScalar
        date.coercing.is(schema.getScalarCoercing(date))

        where:
        executableSchema << executableSchemas()
    }

    def "GraphQLSchema and SUSchema expose normalized applied directives"() {
        given:
        ExecutableSchema schema = executableSchema
        def query = schema.queryType
        def field = schema.getField(query, "value")
        def argument = field.getArgument("input")
        def directive = schema.getDirective("configured")

        expect:
        schema.appliedDirectives*.name == ["meta"]
        schema.appliedDirectives[0].getArgument("value")
                .argumentValue.value.value == "schema"
        schema.getAppliedDirectives(query)*.name == ["meta"]
        schema.getAppliedDirectives(field)*.name == ["meta"]
        schema.getAppliedDirectives(argument)*.name ==
                ["meta", "deprecated"]
        value(schema.getAppliedDirectives(argument)
                .find { it.name == "deprecated" }
                .getArgument("reason").argumentValue.value) == "Use other"
        schema.getAppliedDirectives(directive)*.name == ["meta"]

        and:
        query.appliedDirectives*.name == ["meta"]
        field.appliedDirectives*.name == ["meta"]
        argument.appliedDirectives*.name == ["meta", "deprecated"]
        directive.appliedDirectives*.name == ["meta"]

        and:
        def appliedArgument = schema.getAppliedDirectives(field)[0]
                .getArgument("value")
        appliedArgument instanceof SchemaAppliedDirectiveArgument
        unwrap(appliedArgument.type).name == "String"
        appliedArgument.definition != null

        and:
        (schema.getType("Date") as SchemaScalar).specifiedByUrl ==
                "https://example.com/date"

        where:
        executableSchema << appliedDirectiveSchemas()
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

    def "SUSchema element relationships follow their schema snapshot"() {
        given:
        def universe = new SchemaUniverse()
        def query = universe.newObjectType("Query")
        def node = universe.newInterfaceType("Node")
        def resource = universe.newInterfaceType("Resource")
        def search = universe.newUnionType("Search")
        def alpha = universe.newObjectType("Alpha")
        def beta = universe.newObjectType("Beta")
        def baseSchema = universe.newSchema("base")
                .queryType(query)
                .addInterface(node, resource)
                .addInterface(alpha, node)
                .addUnionMember(search, alpha)
                .addType(beta)
                .build()
        def changedSchema = baseSchema.transform("changed", builder -> builder
                .removeInterface(alpha, node)
                .addInterface(beta, node)
                .removeUnionMember(search, alpha)
                .addUnionMember(search, beta))
        def base = SUExecutableSchema
                .newExecutableSchema(baseSchema)
                .build()
        def changed = SUExecutableSchema
                .newExecutableSchema(changedSchema)
                .build()

        expect:
        (base.getType("Alpha") as SchemaObject).interfaces*.name == ["Node"]
        (base.getType("Beta") as SchemaObject).interfaces.isEmpty()
        (base.getType("Node") as SchemaInterface).interfaces*.name ==
                ["Resource"]
        (base.getType("Search") as SchemaUnion).types*.name == ["Alpha"]

        and:
        (changed.getType("Alpha") as SchemaObject).interfaces.isEmpty()
        (changed.getType("Beta") as SchemaObject).interfaces*.name == ["Node"]
        (changed.getType("Node") as SchemaInterface).interfaces*.name ==
                ["Resource"]
        (changed.getType("Search") as SchemaUnion).types*.name == ["Beta"]
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
        schema.queryType.fieldDefinitions*.name ==
                ["visible", "hidden"]
        schema.getFields(schema.queryType)*.name == ["visible"]
        schema.getField(schema.queryType, "visible") != null
        schema.getField(schema.queryType, "hidden") == null
        schema.getInputFields(filter)*.name == ["visible"]
        filter.fieldDefinitions*.name == ["visible", "hidden"]
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
        ExecutableSchema schema = SUExecutableSchema
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
        schema.getAppliedDirectives(
                schema.getField(schema.queryType, "__schema")).isEmpty()
        schema.getAppliedDirectives(
                schema.getField(schema.queryType, "__type")
                        .getArgument("name")).isEmpty()

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
        def original = SUExecutableSchema
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
        def unbound = SUExecutableSchema
                .newExecutableSchema(universeSchema)
                .build()

        then:
        unbound.getScalarCoercing(
                unbound.getType("String") as SchemaScalar)
                .is(Scalars.GraphQLString.coercing)

        when:
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
        def first = SUExecutableSchema
                .fromGraphQLSchema(firstUniverseSchema, source)
        def second = SUExecutableSchema
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
        SUExecutableSchema.newExecutableSchema(firstUniverseSchema)
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
        def suSchema = SUExecutableSchema
                .fromGraphQLSchema(universeSchema, graphQLSchema)
        return [graphQLSchema, suSchema]
    }

    private static List<ExecutableSchema> appliedDirectiveSchemas() {
        def graphQLSchema = TestUtil.schema('''
            directive @meta(value: String!) repeatable on
                SCHEMA | OBJECT | FIELD_DEFINITION | ARGUMENT_DEFINITION |
                DIRECTIVE_DEFINITION

            directive @configured @meta(value: "directive")
                on FIELD_DEFINITION

            schema @meta(value: "schema") {
                query: Query
            }

            scalar Date
                @specifiedBy(url: "https://example.com/date")

            type Query @meta(value: "query") {
                value(
                    input: String
                        @meta(value: "argument")
                        @deprecated(reason: "Use other")
                ): Date @meta(value: "field")
            }
        ''')
        def universeSchema = new SchemaUniverse()
                .importSchema("applied", graphQLSchema)
        def suSchema = SUExecutableSchema
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

    private static Object value(Object value) {
        if (value instanceof graphql.language.StringValue) {
            return value.value
        }
        return value
    }
}
