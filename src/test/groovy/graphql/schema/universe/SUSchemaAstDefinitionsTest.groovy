package graphql.schema.universe

import graphql.Scalars
import graphql.language.Argument
import graphql.language.Directive
import graphql.language.DirectiveDefinition
import graphql.language.DirectiveExtensionDefinition
import graphql.language.EnumTypeDefinition
import graphql.language.EnumTypeExtensionDefinition
import graphql.language.EnumValueDefinition
import graphql.language.FieldDefinition
import graphql.language.InputObjectTypeDefinition
import graphql.language.InputObjectTypeExtensionDefinition
import graphql.language.InputValueDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.InterfaceTypeExtensionDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.ObjectTypeExtensionDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.ScalarTypeExtensionDefinition
import graphql.language.SchemaDefinition
import graphql.language.SchemaExtensionDefinition
import graphql.language.UnionTypeDefinition
import graphql.language.UnionTypeExtensionDefinition
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.SchemaEnum
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import graphql.schema.universe.view.SUExecutableSchema
import spock.lang.Specification
import spock.lang.Unroll

class SUSchemaAstDefinitionsTest extends Specification {

    @Unroll
    def "captures definitions and extensions from #source import"() {
        when:
        def schema = importSchema(source)

        then:
        schema.getDefinition(schema.root) instanceof SchemaDefinition
        schema.getExtensionDefinitions(schema.root)*.class ==
                [SchemaExtensionDefinition]

        and:
        assertAst(
                schema,
                schema.getObjectType("Query"),
                ObjectTypeDefinition,
                [ObjectTypeExtensionDefinition])
        assertAst(
                schema,
                schema.getInterfaceType("Node"),
                InterfaceTypeDefinition,
                [InterfaceTypeExtensionDefinition])
        assertAst(
                schema,
                schema.getUnionType("Search"),
                UnionTypeDefinition,
                [UnionTypeExtensionDefinition])
        assertAst(
                schema,
                schema.getEnumType("Status"),
                EnumTypeDefinition,
                [EnumTypeExtensionDefinition])
        assertAst(
                schema,
                schema.getScalarType("Date"),
                ScalarTypeDefinition,
                source == "registry"
                        ? [ScalarTypeExtensionDefinition]
                        : [])
        assertAst(
                schema,
                schema.getInputObjectType("Filter"),
                InputObjectTypeDefinition,
                [InputObjectTypeExtensionDefinition])
        assertAst(
                schema,
                schema.getDirectiveDefinition("tag"),
                DirectiveDefinition,
                [DirectiveExtensionDefinition])

        and:
        def query = schema.getObjectType("Query")
        def baseField = schema.getField(query, "node")
        def extensionField = schema.getField(query, "search")
        assertAst(schema, baseField, FieldDefinition)
        assertAst(schema, extensionField, FieldDefinition)
        assertAst(
                schema,
                schema.getArgument(extensionField, "filter"),
                InputValueDefinition)

        and:
        def status = schema.getEnumType("Status")
        assertAst(
                schema,
                schema.getEnumValue(status, "ACTIVE"),
                EnumValueDefinition)
        assertAst(
                schema,
                schema.getEnumValue(status, "INACTIVE"),
                EnumValueDefinition)
        def filter = schema.getInputObjectType("Filter")
        assertAst(
                schema,
                schema.getInputField(filter, "term"),
                InputValueDefinition)
        assertAst(
                schema,
                schema.getInputField(filter, "limit"),
                InputValueDefinition)

        and:
        def schemaTag = schema.getSchemaAppliedDirectives("tag").first()
        schema.getDefinition(schemaTag) instanceof Directive
        schema.getExtensionDefinitions(schemaTag).isEmpty()
        schemaTag.arguments.first().definition instanceof Argument

        where:
        source << ["registry", "graphql"]
    }

    @Unroll
    def "executable view exposes captured AST from #source import"() {
        given:
        def schema = importSchema(source)
        def executable = SUExecutableSchema.newExecutableSchema(schema).build()

        expect:
        executable.definition.is(schema.getDefinition(schema.root))
        executable.extensionDefinitions ==
                schema.getExtensionDefinitions(schema.root)

        and:
        def query = executable.getType("Query")
        query.definition instanceof ObjectTypeDefinition
        query.extensionDefinitions*.class ==
                [ObjectTypeExtensionDefinition]
        executable.getField(query, "node").definition instanceof FieldDefinition
        executable.getField(query, "search")
                .getArgument("filter")
                .definition instanceof InputValueDefinition

        and:
        SchemaEnum status = executable.getType("Status") as SchemaEnum
        status.definition instanceof EnumTypeDefinition
        status.extensionDefinitions*.class ==
                [EnumTypeExtensionDefinition]
        status.getValue("INACTIVE").definition instanceof EnumValueDefinition

        and:
        def directive = executable.getDirective("tag")
        directive.definition instanceof DirectiveDefinition
        directive.extensionDefinitions*.class ==
                [DirectiveExtensionDefinition]

        where:
        source << ["registry", "graphql"]
    }

    @Unroll
    def "capture can be disabled for #source import"() {
        given:
        def options = SUSchemaOptions.defaultOptions()
                .captureAstDefinitions(false)

        when:
        def schema = importSchema(source, options)

        then:
        astVertices(schema).every {
            schema.getDefinition(it) == null &&
                    schema.getExtensionDefinitions(it).isEmpty()
        }

        and:
        appliedDirectives(schema).every { directive ->
            schema.getDefinition(directive) == null &&
                    directive.arguments.every { it.definition == null }
        }

        where:
        source << ["registry", "graphql"]
    }

    def "export and transformed roots retain normalized AST provenance"() {
        given:
        def schema = importSchema("registry")

        when:
        def transformed = schema.transform("transformed", builder -> {})
        def exported = schema.toGraphQLSchema()

        then:
        transformed.getDefinition(transformed.root)
                .is(schema.getDefinition(schema.root))
        transformed.getExtensionDefinitions(transformed.root) ==
                schema.getExtensionDefinitions(schema.root)

        and:
        exported.definition.is(schema.getDefinition(schema.root))
        exported.extensionDefinitions ==
                schema.getExtensionDefinitions(schema.root)
        exported.getObjectType("Query").definition.is(
                schema.getDefinition(schema.getObjectType("Query")))
        exported.getObjectType("Query").extensionDefinitions ==
                schema.getExtensionDefinitions(schema.getObjectType("Query"))
        exported.getDirective("tag").extensionDefinitions ==
                schema.getExtensionDefinitions(
                        schema.getDirectiveDefinition("tag"))
    }

    def "GraphQLSchema import captures wired scalar AST provenance"() {
        given:
        def registry = new SchemaParser().parse('''
            directive @marker on SCALAR
            scalar Date
            extend scalar Date @marker
        ''')
        def scalarDefinition = registry.getType("Date").get()
        def scalarExtensions = registry.scalarTypeExtensions()["Date"]
        def date = GraphQLScalarType.newScalar()
                .name("Date")
                .coercing(Scalars.GraphQLString.coercing)
                .definition(scalarDefinition)
                .extensionDefinitions(scalarExtensions)
                .build()
        def query = GraphQLObjectType.newObject()
                .name("Query")
                .field(GraphQLFieldDefinition.newFieldDefinition()
                        .name("date")
                        .type(date))
                .build()
        GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
                .query(query)
                .additionalType(date)
                .build()

        when:
        def schema = new SchemaUniverse()
                .importSchema("schema", graphQLSchema)

        then:
        schema.getDefinition(schema.getScalarType("Date"))
                .is(scalarDefinition)
        schema.getExtensionDefinitions(schema.getScalarType("Date")) ==
                scalarExtensions
    }

    def "AST printer preserves schema and type extension declarations"() {
        given:
        def schema = importSchema("registry")
        def executable = SUExecutableSchema.newExecutableSchema(schema).build()
        def options = SchemaPrinter.Options.defaultOptions()
                .includeSchemaDefinition(true)
                .useAstDefinitions(true)

        when:
        def printed = new SchemaPrinter(options).print(executable)

        then:
        printed.contains("extend schema")
        printed.contains("extend type Query")
        printed.contains("extend interface Node")
        printed.contains("extend union Search")
        printed.contains("extend enum Status")
        printed.contains("extend scalar Date")
        printed.contains("extend input Filter")
    }

    private static void assertAst(
            SUSchema schema,
            SUVertex vertex,
            Class<?> definitionType,
            List<Class<?>> extensionTypes = []) {
        assert definitionType.isInstance(schema.getDefinition(vertex))
        assert schema.getExtensionDefinitions(vertex)*.class == extensionTypes
    }

    private static SUSchema importSchema(
            String source,
            SUSchemaOptions options = SUSchemaOptions.defaultOptions()) {
        TypeDefinitionRegistry registry =
                new SchemaParser().parse(sdl())
        def universe = new SchemaUniverse()
        if (source == "registry") {
            return universe.importSchema("schema", registry, options)
        }
        def graphQLSchema =
                UnExecutableSchemaGenerator.makeUnExecutableSchema(registry)
        return universe.importSchema("schema", graphQLSchema, options)
    }

    private static List<SUVertex> astVertices(SUSchema schema) {
        def query = schema.getObjectType("Query")
        def status = schema.getEnumType("Status")
        def filter = schema.getInputObjectType("Filter")
        def extensionField = schema.getField(query, "search")
        return [
                schema.root,
                query,
                schema.getInterfaceType("Node"),
                schema.getUnionType("Search"),
                status,
                schema.getScalarType("Date"),
                filter,
                schema.getDirectiveDefinition("tag"),
                schema.getField(query, "node"),
                extensionField,
                schema.getArgument(extensionField, "filter"),
                schema.getEnumValue(status, "ACTIVE"),
                schema.getEnumValue(status, "INACTIVE"),
                schema.getInputField(filter, "term"),
                schema.getInputField(filter, "limit")
        ]
    }

    private static List<SUAppliedDirective> appliedDirectives(
            SUSchema schema) {
        def directives = []
        directives.addAll(schema.schemaAppliedDirectives)
        directives.addAll(
                schema.getAppliedDirectives(schema.getObjectType("Query")))
        directives.addAll(
                schema.getAppliedDirectives(
                        schema.getDirectiveDefinition("tag")))
        return directives
    }

    private static String sdl() {
        return '''
            directive @tag(value: String = "default") repeatable on
              SCHEMA | SCALAR | OBJECT | FIELD_DEFINITION |
              ARGUMENT_DEFINITION | INTERFACE | UNION | ENUM |
              ENUM_VALUE | INPUT_OBJECT | INPUT_FIELD_DEFINITION |
              DIRECTIVE_DEFINITION

            directive @marker(value: String) repeatable on
              DIRECTIVE_DEFINITION

            extend directive @tag @marker(value: "directive extension")

            schema @tag(value: "schema") {
              query: Query
            }

            extend schema @tag(value: "schema extension")

            scalar Date
            extend scalar Date @tag

            interface Node {
              id: ID!
            }

            extend interface Node @tag {
              label: String
            }

            type Item implements Node {
              id: ID!
              label: String
            }

            type Other implements Node {
              id: ID!
              label: String
            }

            union Search = Item
            extend union Search @tag = Other

            enum Status {
              ACTIVE
            }

            extend enum Status @tag {
              INACTIVE @tag
            }

            input Filter {
              term: String
            }

            extend input Filter @tag {
              limit: Int = 10
            }

            type Query @tag {
              node: Node
            }

            extend type Query @tag(value: "type extension") {
              search(filter: Filter = {limit: 5}): Search @tag
            }
        '''
    }
}
