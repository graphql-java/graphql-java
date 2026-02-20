package graphql.schema.idl;

import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.language.OperationTypeDefinition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.errors.SchemaProblem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.schema.idl.SchemaGeneratorHelper.buildDescription;

/**
 * A schema generator that uses {@link GraphQLSchema.FastBuilder} to construct the schema.
 * {@link GraphQLSchema.FastBuilder} has a number of important limitations, so please read
 * its documentation carefully to understand if you should use this instead of the standard
 * {@link SchemaGenerator}.
 *
 * @see GraphQLSchema.FastBuilder
 * @see SchemaGenerator
 */
@ExperimentalApi
@NullMarked
public class FastSchemaGenerator {

    private final SchemaTypeChecker typeChecker = new SchemaTypeChecker();
    private final SchemaGeneratorHelper schemaGeneratorHelper = new SchemaGeneratorHelper();

    /**
     * Creates an executable schema from a TypeDefinitionRegistry using FastBuilder.
     *
     * @param typeRegistry the type definition registry
     * @param wiring       the runtime wiring
     * @return a validated, executable schema
     */
    public GraphQLSchema makeExecutableSchema(TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
        return makeExecutableSchema(SchemaGenerator.Options.defaultOptions(), typeRegistry, wiring);
    }

    /**
     * Creates an executable schema from a TypeDefinitionRegistry using FastBuilder.
     *
     * @param options      the schema generation options
     * @param typeRegistry the type definition registry
     * @param wiring       the runtime wiring
     * @return an executable schema
     */
    public GraphQLSchema makeExecutableSchema(SchemaGenerator.Options options,
                                              TypeDefinitionRegistry typeRegistry,
                                              RuntimeWiring wiring) {
        // Make a copy and add default directives
        TypeDefinitionRegistry typeRegistryCopy = new TypeDefinitionRegistry();
        typeRegistryCopy.merge(typeRegistry);
        schemaGeneratorHelper.addDirectivesIncludedByDefault(typeRegistryCopy);

        // Use immutable registry for faster operations
        ImmutableTypeDefinitionRegistry fasterImmutableRegistry = typeRegistryCopy.readOnly();

        // Check type registry for errors
        List<GraphQLError> errors = typeChecker.checkTypeRegistry(fasterImmutableRegistry, wiring);
        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        Map<String, OperationTypeDefinition> operationTypeDefinitions = SchemaExtensionsChecker.gatherOperationDefs(fasterImmutableRegistry);

        return makeExecutableSchemaImpl(fasterImmutableRegistry, wiring, operationTypeDefinitions, options);
    }

    private GraphQLSchema makeExecutableSchemaImpl(ImmutableTypeDefinitionRegistry typeRegistry,
                                                   RuntimeWiring wiring,
                                                   Map<String, OperationTypeDefinition> operationTypeDefinitions,
                                                   SchemaGenerator.Options options) {
        // Build all types using the standard helper
        SchemaGeneratorHelper.BuildContext buildCtx = new SchemaGeneratorHelper.BuildContext(
                typeRegistry, wiring, operationTypeDefinitions, options);

        // Build directives
        Set<GraphQLDirective> additionalDirectives = schemaGeneratorHelper.buildAdditionalDirectiveDefinitions(buildCtx);

        // Use a dummy builder to trigger type building (this populates buildCtx)
        GraphQLSchema.Builder tempBuilder = GraphQLSchema.newSchema();
        schemaGeneratorHelper.buildOperations(buildCtx, tempBuilder);

        // Build all additional types
        Set<GraphQLNamedType> additionalTypes = schemaGeneratorHelper.buildAdditionalTypes(buildCtx);

        // Set field visibility on code registry
        buildCtx.getCodeRegistry().fieldVisibility(buildCtx.getWiring().getFieldVisibility());

        // Build the code registry
        GraphQLCodeRegistry codeRegistry = buildCtx.getCodeRegistry().build();

        // Extract operation types by name from built types (all types from buildCtx are named types)
        Set<GraphQLNamedType> allBuiltTypes = buildCtx.getTypes().stream()
                .map(t -> (GraphQLNamedType) t)
                .collect(Collectors.toSet());

        // Get the actual type names from operationTypeDefinitions, defaulting to standard names
        String queryTypeName = getOperationTypeName(operationTypeDefinitions, "query", "Query");
        String mutationTypeName = getOperationTypeName(operationTypeDefinitions, "mutation", "Mutation");
        String subscriptionTypeName = getOperationTypeName(operationTypeDefinitions, "subscription", "Subscription");

        GraphQLObjectType queryType = findOperationType(allBuiltTypes, queryTypeName);
        GraphQLObjectType mutationType = findOperationType(allBuiltTypes, mutationTypeName);
        GraphQLObjectType subscriptionType = findOperationType(allBuiltTypes, subscriptionTypeName);

        if (queryType == null) {
            throw new IllegalStateException("Query type '" + queryTypeName + "' is required but was not found");
        }

        // Create FastBuilder
        GraphQLSchema.FastBuilder fastBuilder = new GraphQLSchema.FastBuilder(
                GraphQLCodeRegistry.newCodeRegistry(codeRegistry),
                queryType,
                mutationType,
                subscriptionType);

        // Add all built types
        fastBuilder.addTypes(allBuiltTypes);
        fastBuilder.addTypes(additionalTypes);

        // Add all directive definitions
        fastBuilder.additionalDirectives(additionalDirectives);

        // Add schema description and definition if present
        typeRegistry.schemaDefinition().ifPresent(schemaDefinition -> {
            String description = buildDescription(buildCtx, schemaDefinition, schemaDefinition.getDescription());
            fastBuilder.description(description);
            fastBuilder.definition(schemaDefinition);
        });

        // Configure validation
        fastBuilder.withValidation(options.isWithValidation());

        return fastBuilder.build();
    }

    private String getOperationTypeName(Map<String, OperationTypeDefinition> operationTypeDefs,
                                         String operationName,
                                         String defaultTypeName) {
        OperationTypeDefinition opDef = operationTypeDefs.get(operationName);
        if (opDef != null) {
            return opDef.getTypeName().getName();
        }
        return defaultTypeName;
    }

    private @Nullable GraphQLObjectType findOperationType(Set<GraphQLNamedType> types, String typeName) {
        for (GraphQLNamedType type : types) {
            if (type instanceof GraphQLObjectType) {
                GraphQLObjectType objectType = (GraphQLObjectType) type;
                if (objectType.getName().equals(typeName)) {
                    return objectType;
                }
            }
        }
        return null;
    }
}
