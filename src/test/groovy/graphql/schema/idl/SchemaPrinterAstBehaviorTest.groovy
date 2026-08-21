package graphql.schema.idl

import graphql.Scalars
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLEnumValueDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLSchemaElement
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver

import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLEnumType.newEnum
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLUnionType.newUnionType
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.SchemaPrinter.Options.defaultOptions

class SchemaPrinterAstBehaviorTest extends AbstractSchemaPrintingTest {

    def "AST-aware semantic fallback remains equivalent to the semantic writer"() {
        given:
        def schema = programmaticSchema()
        def optionSets = [
                defaultOptions()
                        .includeSchemaDefinition(true)
                        .includeScalarTypes(true)
                        .includeDirectives(true),
                defaultOptions()
                        .includeSchemaDefinition(true)
                        .includeScalarTypes(true)
                        .descriptionsAsHashComments(true),
                defaultOptions()
                        .includeSchemaDefinition(false)
                        .includeScalarTypes(false)
                        .includeDirectives(false)
                        .includeDirectiveDefinitions(false)
        ]

        expect:
        optionSets.every { options ->
            def semantic = printSchema(new SchemaPrinter(
                    options.useAstDefinitions(false)), schema)
            def astAwareFallback = printSchema(new SchemaPrinter(
                    options.useAstDefinitions(true)), schema)
            astAwareFallback == semantic
        }
    }

    def "AST-aware semantic fallback handles default and omitted schema elements"() {
        given:
        def schema = minimalSchema()
        def optionSets = [
                defaultOptions()
                        .includeSchemaDefinition(true),
                defaultOptions()
                        .includeSchemaDefinition(false),
                defaultOptions()
                        .includeSchemaDefinition(true)
                        .includeDirectiveDefinition { false },
                defaultOptions()
                        .includeSchemaDefinition(true)
                        .includeDirectiveDefinitions(false),
                defaultOptions()
                        .includeSchemaDefinition(true)
                        .includeIntrospectionTypes(true)
        ]

        expect:
        optionSets.every { options ->
            def semantic = printSchema(new SchemaPrinter(
                    options.useAstDefinitions(false)), schema)
            def astAwareFallback = printSchema(new SchemaPrinter(
                    options.useAstDefinitions(true)), schema)
            astAwareFallback == semantic
        }
    }

    def "direct schema element printing retains AST comments for every schema element kind"() {
        given:
        def registry = new SchemaParser().parse(commentsEverywhereSdl())
        def wiring = newRuntimeWiring()
                .scalar(TestUtil.mockScalar(registry.scalars().get("Custom")))
                .type(TestUtil.mockTypeRuntimeWiring("Node", true))
                .type(TestUtil.mockTypeRuntimeWiring("Search", true))
                .build()
        def generatorOptions =
                SchemaGenerator.Options.defaultOptions().useCommentsAsDescriptions(false)
        def schema =
                new SchemaGenerator().makeExecutableSchema(generatorOptions, registry, wiring)
        List<GraphQLSchemaElement> elements = []
        elements.addAll(schema.allTypesAsList.findAll { !it.name.startsWith("__") })
        elements.addAll(schema.directives)
        def printerOptions = defaultOptions()
                .includeScalarTypes(true)
                .includeAstDefinitionComments(true)
                .descriptionsAsHashComments(true)

        when:
        def result = printElements(
                new SchemaPrinter(printerOptions),
                schema,
                elements)

        then:
        [
                "# scalar comment",
                "# interface comment",
                "# interface field comment",
                "# interface argument comment",
                "# object comment",
                "# union comment",
                "# enum comment",
                "# enum value comment",
                "# input object comment",
                "# input field comment",
                "# directive comment",
                "# directive argument comment"
        ].every { result.contains(it) }
    }

    private static GraphQLSchema programmaticSchema() {
        def appliedTag = GraphQLAppliedDirective.newDirective()
                .name("tag")
                .argument(GraphQLAppliedDirectiveArgument.newArgument()
                        .name("label")
                        .type(Scalars.GraphQLString)
                        .valueProgrammatic("schema")
                        .build())
                .build()
        def tagDefinition = GraphQLDirective.newDirective()
                .name("tag")
                .description("A repeatable tag")
                .repeatable(true)
                .validLocations(
                        Introspection.DirectiveLocation.SCHEMA,
                        Introspection.DirectiveLocation.OBJECT,
                        Introspection.DirectiveLocation.FIELD_DEFINITION)
                .argument(newArgument()
                        .name("label")
                        .description("Tag label")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()
        GraphQLScalarType custom = newScalar()
                .name("Custom")
                .description("A custom\nscalar")
                .specifiedByUrl("https://example.com/custom")
                .coercing(TestUtil.mockCoercing())
                .build()
        GraphQLInterfaceType node = newInterface()
                .name("Node")
                .description("A node")
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .build()
        GraphQLInterfaceType resource = newInterface()
                .name("Resource")
                .description("A resource")
                .withInterface(node)
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .field(newFieldDefinition()
                        .name("url")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()
        GraphQLEnumType status = newEnum()
                .name("Status")
                .description("Result status")
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition()
                        .name("CURRENT")
                        .build())
                .value(GraphQLEnumValueDefinition.newEnumValueDefinition()
                        .name("OLD")
                        .description("Old status")
                        .deprecationReason("Use CURRENT")
                        .build())
                .build()
        GraphQLInputObjectType filter = newInputObject()
                .name("Filter")
                .description("Search filter")
                .field(newInputObjectField()
                        .name("limit")
                        .description("Result limit")
                        .type(Scalars.GraphQLInt)
                        .defaultValueProgrammatic(10)
                        .build())
                .field(newInputObjectField()
                        .name("old")
                        .type(Scalars.GraphQLString)
                        .deprecate("Use limit")
                        .build())
                .build()
        GraphQLObjectType first = newObject()
                .name("FirstResult")
                .description("First result")
                .withInterfaces(node, resource)
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .field(newFieldDefinition()
                        .name("url")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()
        GraphQLObjectType second = newObject()
                .name("SecondResult")
                .description("Second result")
                .withInterfaces(node, resource)
                .field(newFieldDefinition()
                        .name("id")
                        .type(Scalars.GraphQLID)
                        .build())
                .field(newFieldDefinition()
                        .name("url")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()
        GraphQLUnionType search = newUnionType()
                .name("Search")
                .description("Search result")
                .possibleTypes(first, second)
                .build()
        GraphQLObjectType query = newObject()
                .name("RootQuery")
                .description("Query root")
                .withAppliedDirective(appliedTag)
                .field(newFieldDefinition()
                        .name("search")
                        .description("Search field")
                        .type(search)
                        .deprecate("Use currentSearch")
                        .argument(newArgument()
                                .name("filter")
                                .description("Filter argument")
                                .type(filter)
                                .deprecate("Use options")
                                .build())
                        .build())
                .field(newFieldDefinition()
                        .name("custom")
                        .type(custom)
                        .build())
                .field(newFieldDefinition()
                        .name("status")
                        .type(status)
                        .build())
                .build()
        GraphQLObjectType mutation = newObject()
                .name("RootMutation")
                .field(newFieldDefinition()
                        .name("change")
                        .type(first)
                        .build())
                .build()
        GraphQLObjectType subscription = newObject()
                .name("RootSubscription")
                .field(newFieldDefinition()
                        .name("changed")
                        .type(second)
                        .build())
                .build()
        TypeResolver firstResultResolver = { environment -> first } as TypeResolver
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
                .typeResolver(node, firstResultResolver)
                .typeResolver(resource, firstResultResolver)
                .typeResolver(search, firstResultResolver)
                .build()

        return GraphQLSchema.newSchema()
                .description("Programmatic\nschema")
                .query(query)
                .mutation(mutation)
                .subscription(subscription)
                .additionalDirective(tagDefinition)
                .withSchemaAppliedDirective(appliedTag)
                .codeRegistry(codeRegistry)
                .build()
    }

    private static GraphQLSchema minimalSchema() {
        def query = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .build())
                .build()
        return GraphQLSchema.newSchema()
                .query(query)
                .build()
    }

    private static String commentsEverywhereSdl() {
        return '''
            # directive comment
            directive @tag(
              # directive argument comment
              label: String
            ) on OBJECT

            # scalar comment
            scalar Custom

            # interface comment
            interface Node {
              # interface field comment
              id(
                # interface argument comment
                format: String
              ): ID
            }

            # object comment
            type First implements Node {
              id(format: String): ID
            }

            type Second implements Node {
              id(format: String): ID
            }

            # union comment
            union Search = First | Second

            # enum comment
            enum Status {
              # enum value comment
              OLD
            }

            # input object comment
            input Filter {
              # input field comment
              value: String
            }

            type Query {
              search(filter: Filter, status: Status): Search
              custom: Custom
            }
        '''
    }
}
