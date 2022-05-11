package graphql

import graphql.execution.MergedField
import graphql.execution.MergedSelectionSet
import graphql.language.Document
import graphql.language.Field
import graphql.language.NullValue
import graphql.language.ObjectTypeDefinition
import graphql.language.OperationDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.Type
import graphql.parser.Parser
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.GraphQLAppliedDirectiveArgument
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.TypeResolver
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TestMockedWiringFactory
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.idl.WiringFactory
import graphql.schema.idl.errors.SchemaProblem
import groovy.json.JsonOutput

import java.util.function.Supplier
import java.util.stream.Collectors

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLDirective.newDirective
import static graphql.schema.GraphQLFieldDefinition.Builder
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static graphql.schema.GraphQLSchema.newSchema

class TestUtil {


    static GraphQLSchema schemaWithInputType(GraphQLInputType inputType) {
        GraphQLArgument.Builder fieldArgument = newArgument().name("arg").type(inputType)
        Builder name = newFieldDefinition()
                .name("name").type(GraphQLString).argument(fieldArgument)
        GraphQLObjectType queryType = newObject().name("query").field(name).build()
        newSchema().query(queryType).build()
    }

    static dummySchema = newSchema()
            .query(newObject()
                    .name("QueryType")
                    .field(newFieldDefinition().name("field").type(GraphQLString))
                    .build())
            .build()

    static GraphQLSchema schemaFile(String fileName) {
        return schemaFile(fileName, mockRuntimeWiring)
    }


    static GraphQLSchema schemaFromResource(String resourceFileName, RuntimeWiring wiring) {
        def stream = TestUtil.class.getClassLoader().getResourceAsStream(resourceFileName)
        return schema(stream, wiring)
    }


    static GraphQLSchema schemaFile(String fileName, RuntimeWiring wiring) {
        def stream = TestUtil.class.getClassLoader().getResourceAsStream(fileName)

        def typeRegistry = new SchemaParser().parse(new InputStreamReader(stream))
        def options = SchemaGenerator.Options.defaultOptions()
        def schema = new SchemaGenerator().makeExecutableSchema(options, typeRegistry, wiring)
        schema
    }

    static GraphQLSchema schema(String spec, Map<String, Map<String, DataFetcher>> dataFetchers) {
        def wiring = RuntimeWiring.newRuntimeWiring()
        dataFetchers.each { type, fieldFetchers ->
            def tw = TypeRuntimeWiring.newTypeWiring(type).dataFetchers(fieldFetchers)
            wiring.type(tw)
        }
        schema(spec, wiring)
    }

    static GraphQLSchema schema(String spec, RuntimeWiring.Builder runtimeWiring) {
        schema(spec, runtimeWiring.build())
    }

    static GraphQLSchema schema(String spec) {
        schema(spec, mockRuntimeWiring)
    }

    static GraphQLSchema schema(Reader specReader) {
        schema(specReader, mockRuntimeWiring)
    }

    static GraphQLSchema schema(String spec, RuntimeWiring runtimeWiring) {
        schema(new StringReader(spec), runtimeWiring)
    }

    static GraphQLSchema schema(InputStream specStream, RuntimeWiring runtimeWiring) {
        schema(new InputStreamReader(specStream), runtimeWiring)
    }

    static GraphQLSchema schema(Reader specReader, RuntimeWiring runtimeWiring) {
        try {
            def registry = new SchemaParser().parse(specReader)
            def options = SchemaGenerator.Options.defaultOptions()
            return new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)
        } catch (SchemaProblem e) {
            assert false: "The schema could not be compiled : ${e}"
            return null
        }
    }

    static GraphQLSchema schema(SchemaGenerator.Options options, String spec, RuntimeWiring runtimeWiring) {
        try {
            def registry = new SchemaParser().parse(spec)
            return new SchemaGenerator().makeExecutableSchema(options, registry, runtimeWiring)
        } catch (SchemaProblem e) {
            assert false: "The schema could not be compiled : ${e}"
            return null
        }
    }

    static GraphQL.Builder graphQL(String spec) {
        return graphQL(new StringReader(spec), mockRuntimeWiring)
    }

    static GraphQL.Builder graphQL(String spec, RuntimeWiring runtimeWiring) {
        return graphQL(new StringReader(spec), runtimeWiring)
    }

    static GraphQL.Builder graphQL(String spec, RuntimeWiring.Builder runtimeWiring) {
        return graphQL(new StringReader(spec), runtimeWiring.build())
    }

    static GraphQL.Builder graphQL(String spec, Map<String, Map<String, DataFetcher>> dataFetchers) {
        toGraphqlBuilder({ -> schema(spec, dataFetchers) })
    }

    static GraphQL.Builder graphQL(Reader specReader, RuntimeWiring runtimeWiring) {
        return toGraphqlBuilder({ -> schema(specReader, runtimeWiring) })
    }

    private static GraphQL.Builder toGraphqlBuilder(Supplier<GraphQLSchema> supplier) {
        try {
            def schema = supplier.get()
            return GraphQL.newGraphQL(schema)
        } catch (SchemaProblem e) {
            assert false: "The schema could not be compiled : ${e}"
            return null
        }
    }

    static WiringFactory mockWiringFactory = new TestMockedWiringFactory()

    static RuntimeWiring mockRuntimeWiring = RuntimeWiring.newRuntimeWiring().wiringFactory(mockWiringFactory).build()

    static GraphQLScalarType mockScalar(String name) {
        newScalar().name(name).description(name).coercing(mockCoercing()).build()
    }

    static Coercing mockCoercing() {
        new Coercing() {
            @Override
            Object serialize(Object dataFetcherResult) {
                return null
            }

            @Override
            Object parseValue(Object input) {
                return null
            }

            @Override
            Object parseLiteral(Object input) {
                return NullValue.newNullValue().build()
            }
        }
    }

    static GraphQLScalarType mockScalar(ScalarTypeDefinition definition) {
        newScalar()
                .name(definition.getName())
                .description(definition.getDescription() == null ? null : definition.getDescription().getContent())
                .coercing(mockCoercing())
                .replaceDirectives(definition.getDirectives().stream().map({ mockDirective(it.getName()) }).collect(Collectors.toList()))
                .definition(definition)
                .build()
    }

    static GraphQLDirective mockDirective(String name) {
        newDirective().name(name).description(name).build()
    }

    static TypeRuntimeWiring mockTypeRuntimeWiring(String typeName, boolean withResolver) {
        def builder = TypeRuntimeWiring.newTypeWiring(typeName)
        if (withResolver) {
            builder.typeResolver(new TypeResolver() {
                @Override
                GraphQLObjectType getType(TypeResolutionEnvironment env) {
                    return null
                }
            })
        }
        return builder.build()
    }


    static Document parseQuery(String query) {
        new Parser().parseDocument(query)
    }

    static Type parseType(String typeAst) {
        String docStr = """
            type X {
                field : $typeAst
            }
        """
        try {
            def document = toDocument(docStr)
            ObjectTypeDefinition objTypeDef = document.getDefinitionsOfType(ObjectTypeDefinition.class)[0]
            return objTypeDef.fieldDefinitions[0].getType()
        } catch (Exception ignored) {
            assert false, "Invalid type AST string : $typeAst"
            return null
        }
    }

    static Document toDocument(String query) {
        parseQuery(query)
    }

    static MergedField mergedField(List<Field> fields) {
        return MergedField.newMergedField(fields).build()
    }

    static MergedField mergedField(Field field) {
        return MergedField.newMergedField(field).build()
    }

    static MergedSelectionSet mergedSelectionSet(Map<String, MergedField> subFields) {
        return MergedSelectionSet.newMergedSelectionSet().subFields(subFields).build()
    }

    static Field parseField(String sdlField) {
        String spec = """ query Foo {
        $sdlField
        }
        """
        def document = parseQuery(spec)
        def op = document.getDefinitionsOfType(OperationDefinition.class)[0]
        return op.getSelectionSet().getSelectionsOfType(Field.class)[0] as Field
    }

    static GraphQLAppliedDirective[] mockDirectivesWithArguments(String... names) {
        return names.collect { directiveName ->
            def builder = GraphQLAppliedDirective.newDirective().name(directiveName)

            names.each { argName ->
                builder.argument(GraphQLAppliedDirectiveArgument.newArgument().name(argName).type(GraphQLInt).valueProgrammatic(BigInteger.valueOf(0)).build())
            }
            return builder.build()
        }.toArray() as GraphQLAppliedDirective[]
    }

    static GraphQLAppliedDirective[] mockDirectivesWithNoValueArguments(String... names) {
        return names.collect { directiveName ->
            def builder = GraphQLAppliedDirective.newDirective().name(directiveName)

            names.each { argName ->
                builder.argument(GraphQLAppliedDirectiveArgument.newArgument().name(argName).type(GraphQLInt).build())
            }
            return builder.build()
        }.toArray() as GraphQLAppliedDirective[]
    }

    static List<GraphQLArgument> mockArguments(String... names) {
        return names.collect { newArgument().name(it).type(GraphQLInt).build() }
    }

    static List<GraphQLAppliedDirectiveArgument> mockAppliedArguments(String... names) {
        return names.collect { newArgument().name(it).type(GraphQLInt).build() }
    }

    static Comparator<? super GraphQLType> byGreatestLength = Comparator.comparing({ it.name },
            Comparator.comparing({ it.length() }).reversed())


    /**
     * Turns a object kinto JSON and prints it - Helpful for debugging
     * @param obj some obj
     * @return a string
     */
    static String prettyPrint(Object obj) {
        if (obj instanceof ExecutionResult) {
            obj = ((ExecutionResult) obj).toSpecification()
        }
        return JsonOutput.prettyPrint(JsonOutput.toJson(obj))

    }
}
