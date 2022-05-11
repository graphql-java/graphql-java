package graphql.schema.idl;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.OperationTypeDefinition;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.schema.idl.SchemaGeneratorHelper.buildDescription;


/**
 * This can generate a working runtime schema from a type registry and runtime wiring
 */
@PublicApi
public class SchemaGenerator {

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();
    private final SchemaGeneratorHelper schemaGeneratorHelper = new SchemaGeneratorHelper();

    public SchemaGenerator() {
    }

    /**
     * Created a schema from the SDL that is has a mocked runtime.
     *
     * @param sdl the SDL to be mocked
     *
     * @return a schema with a mocked runtime
     *
     * @see RuntimeWiring#MOCKED_WIRING
     */
    public static GraphQLSchema createdMockedSchema(String sdl) {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, RuntimeWiring.MOCKED_WIRING);
        return graphQLSchema;
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

        return makeExecutableSchemaImpl(typeRegistryCopy, wiring, operationTypeDefinitions, options);
    }

    private GraphQLSchema makeExecutableSchemaImpl(TypeDefinitionRegistry typeRegistry,
                                                   RuntimeWiring wiring,
                                                   Map<String, OperationTypeDefinition> operationTypeDefinitions,
                                                   Options options) {
        SchemaGeneratorHelper.BuildContext buildCtx = new SchemaGeneratorHelper.BuildContext(typeRegistry, wiring, operationTypeDefinitions, options);

        GraphQLSchema.Builder schemaBuilder = GraphQLSchema.newSchema();

        Set<GraphQLDirective> additionalDirectives = schemaGeneratorHelper.buildAdditionalDirectiveDefinitions(buildCtx);
        schemaBuilder.additionalDirectives(additionalDirectives);

        schemaGeneratorHelper.buildSchemaDirectivesAndExtensions(buildCtx, schemaBuilder);

        schemaGeneratorHelper.buildOperations(buildCtx, schemaBuilder);

        Set<GraphQLType> additionalTypes = schemaGeneratorHelper.buildAdditionalTypes(buildCtx);
        schemaBuilder.additionalTypes(additionalTypes);

        buildCtx.getCodeRegistry().fieldVisibility(buildCtx.getWiring().getFieldVisibility());

        GraphQLCodeRegistry codeRegistry = buildCtx.getCodeRegistry().build();
        schemaBuilder.codeRegistry(codeRegistry);

        buildCtx.getTypeRegistry().schemaDefinition().ifPresent(schemaDefinition -> {
            String description = buildDescription(buildCtx, schemaDefinition, schemaDefinition.getDescription());
            schemaBuilder.description(description);
        });
        GraphQLSchema graphQLSchema = schemaBuilder.build();

        List<SchemaGeneratorPostProcessing> schemaTransformers = new ArrayList<>();
        // we check if there are any SchemaDirectiveWiring's in play and if there are
        // we add this to enable them.  By not adding it always, we save unnecessary
        // schema build traversals
        if (buildCtx.isDirectiveWiringRequired()) {
            // handle directive wiring AFTER the schema has been built and hence type references are resolved at callback time
            schemaTransformers.add(
                    new SchemaDirectiveWiringSchemaGeneratorPostProcessing(
                            buildCtx.getTypeRegistry(),
                            buildCtx.getWiring(),
                            buildCtx.getCodeRegistry())
            );
        }
        schemaTransformers.addAll(buildCtx.getWiring().getSchemaGeneratorPostProcessings());

        for (SchemaGeneratorPostProcessing postProcessing : schemaTransformers) {
            graphQLSchema = postProcessing.process(graphQLSchema);
        }
        return graphQLSchema;
    }

    /**
     * These options control how the schema generation works
     */
    public static class Options {
        private final boolean useCommentsAsDescription;
        private final boolean captureAstDefinitions;
        private final boolean useAppliedDirectivesOnly;

        Options(boolean useCommentsAsDescription, boolean captureAstDefinitions, boolean useAppliedDirectivesOnly) {
            this.useCommentsAsDescription = useCommentsAsDescription;
            this.captureAstDefinitions = captureAstDefinitions;
            this.useAppliedDirectivesOnly = useAppliedDirectivesOnly;
        }

        public boolean isUseCommentsAsDescription() {
            return useCommentsAsDescription;
        }

        public boolean isCaptureAstDefinitions() {
            return captureAstDefinitions;
        }

        public boolean isUseAppliedDirectivesOnly() {
            return useAppliedDirectivesOnly;
        }

        public static Options defaultOptions() {
            return new Options(true, true, false);
        }

        /**
         * This controls whether # comments can be used as descriptions in the built schema.  For specification legacy reasons
         * # comments used to be used as schema element descriptions.  The specification has since clarified this and "" quoted string
         * descriptions are the sanctioned way to make scheme element descriptions.
         *
         * @param useCommentsAsDescription the flag to control whether comments can be used as schema element descriptions
         *
         * @return a new Options object
         */
        public Options useCommentsAsDescriptions(boolean useCommentsAsDescription) {
            return new Options(useCommentsAsDescription, captureAstDefinitions, useAppliedDirectivesOnly);
        }

        /**
         * Memory can be saved if the original AST definitions are not associated with the built runtime types.  However
         * some tooling may require them.
         *
         * @param captureAstDefinitions the flag on whether to capture AST definitions
         *
         * @return a new Options object
         */
        public Options captureAstDefinitions(boolean captureAstDefinitions) {
            return new Options(useCommentsAsDescription, captureAstDefinitions, useAppliedDirectivesOnly);
        }

        /**
         * The class {@link GraphQLDirective} should really represent the definition of a directive, and not its use on schema elements.
         * The new {@link graphql.schema.GraphQLAppliedDirective} has been created to fix this however for legacy reasons both classes will be put on schema
         * elements.  This flag allows you to only use {@link graphql.schema.GraphQLAppliedDirective} on schema elements.
         *
         * @param useAppliedDirectivesOnly the flag on whether to use {@link graphql.schema.GraphQLAppliedDirective}s only on schema elements
         *
         * @return a new Options object
         */
        public Options useAppliedDirectivesOnly(boolean useAppliedDirectivesOnly) {
            return new Options(useCommentsAsDescription, captureAstDefinitions, useAppliedDirectivesOnly);
        }
    }
}