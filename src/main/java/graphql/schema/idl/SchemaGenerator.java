package graphql.schema.idl;

import graphql.PublicApi;
import graphql.GraphQLError;
import graphql.language.OperationTypeDefinition;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This can generate a working runtime schema from a type registry and runtime wiring
 */
@PublicApi
public class SchemaGenerator {

    /**
     * These options control how the schema generation works
     */
    public static class Options {

        Options() {
        }

        public static Options defaultOptions() {
            return new Options();
        }
    }

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();
    private final SchemaGeneratorHelper schemaGeneratorHelper = new SchemaGeneratorHelper();

    public SchemaGenerator() {
    }

    /**
     * This will take a {@link TypeDefinitionRegistry} and a {@link RuntimeWiring} and put them together to create a executable schema
     *
     * @param typeRegistry this can be obtained via {@link SchemaParser#parse(String)}
     * @param wiring       this can be built using {@link RuntimeWiring#newRuntimeWiring()}
     *
     * @return an executable schema
     *
     * @throws SchemaProblem if there are problems in assembling a schema such as missing type resolvers or no operations defined
     */
    public GraphQLSchema makeExecutableSchema(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {
        return makeExecutableSchema(Options.defaultOptions(), typeRegistry, wiring);
    }

    /**
     * This will take a {@link TypeDefinitionRegistry} and a {@link RuntimeWiring} and put them together to create a executable schema
     * controlled by the provided options.
     *
     * @param options      the controlling options
     * @param typeRegistry this can be obtained via {@link SchemaParser#parse(String)}
     * @param wiring       this can be built using {@link RuntimeWiring#newRuntimeWiring()}
     *
     * @return an executable schema
     *
     * @throws SchemaProblem if there are problems in assembling a schema such as missing type resolvers or no operations defined
     */
    public GraphQLSchema makeExecutableSchema(Options options, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) throws SchemaProblem {

        TypeDefinitionRegistry typeRegistryCopy = new TypeDefinitionRegistry();
        typeRegistryCopy.merge(typeRegistry);

        schemaGeneratorHelper.addDirectivesIncludedByDefault(typeRegistryCopy);

        List<GraphQLError> errors = typeChecker.checkTypeRegistry(typeRegistryCopy, wiring);
        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        Map<String, OperationTypeDefinition> operationTypeDefinitions = SchemaExtensionsChecker.gatherOperationDefs(typeRegistry);

        return makeExecutableSchemaImpl(typeRegistryCopy, wiring, operationTypeDefinitions);
    }

    private GraphQLSchema makeExecutableSchemaImpl(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring, Map<String, OperationTypeDefinition> operationTypeDefinitions) {
        SchemaGeneratorHelper.BuildContext buildCtx = new SchemaGeneratorHelper.BuildContext(typeRegistry, wiring, operationTypeDefinitions);

        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();

        Set<GraphQLDirective> additionalDirectives = schemaGeneratorHelper.buildAdditionalDirectives(buildCtx);
        schemaBuilder.additionalDirectives(additionalDirectives);

        schemaGeneratorHelper.buildSchemaDirectivesAndExtensions(buildCtx, schemaBuilder);

        schemaGeneratorHelper.buildOperations(buildCtx, schemaBuilder);

        Set<GraphQLType> additionalTypes = schemaGeneratorHelper.buildAdditionalTypes(buildCtx);
        schemaBuilder.additionalTypes(additionalTypes);

        buildCtx.getCodeRegistry().fieldVisibility(buildCtx.getWiring().getFieldVisibility());

        GraphQLCodeRegistry codeRegistry = buildCtx.getCodeRegistry().build();
        schemaBuilder.codeRegistry(codeRegistry);

        buildCtx.getTypeRegistry().schemaDefinition().ifPresent(schemaDefinition -> {
            String description = schemaGeneratorHelper.buildDescription(schemaDefinition, schemaDefinition.getDescription());
            schemaBuilder.description(description);
        });
        GraphQLSchema graphQLSchema = schemaBuilder.build();

        Collection<SchemaGeneratorPostProcessing> schemaTransformers = buildCtx.getWiring().getSchemaGeneratorPostProcessings();
        for (SchemaGeneratorPostProcessing postProcessing : schemaTransformers) {
            graphQLSchema = postProcessing.process(graphQLSchema);
        }
        return graphQLSchema;
    }
}