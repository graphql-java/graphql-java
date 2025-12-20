package graphql.schema.idl;

import graphql.Internal;
import graphql.language.OperationTypeDefinition;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;

import java.util.Map;
import java.util.Set;

import static graphql.schema.idl.SchemaGeneratorHelper.buildDescription;

/**
 * A schema generator that uses GraphQLSchema.FastBuilder for improved performance.
 * This is intended for benchmarking and performance testing purposes.
 */
@Internal
public class FastSchemaGenerator {

    private final SchemaGeneratorHelper schemaGeneratorHelper = new SchemaGeneratorHelper();

    /**
     * Creates an executable schema from a TypeDefinitionRegistry using FastBuilder.
     * This method is optimized for performance and skips validation by default.
     *
     * @param typeRegistry the type definition registry
     * @param wiring       the runtime wiring
     * @return an executable schema
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
    public GraphQLSchema makeExecutableSchema(SchemaGenerator.Options options, TypeDefinitionRegistry typeRegistry, RuntimeWiring wiring) {
        // Make a copy and add default directives
        TypeDefinitionRegistry typeRegistryCopy = new TypeDefinitionRegistry();
        typeRegistryCopy.merge(typeRegistry);
        schemaGeneratorHelper.addDirectivesIncludedByDefault(typeRegistryCopy);

        // Use immutable registry for faster operations
        ImmutableTypeDefinitionRegistry fasterImmutableRegistry = typeRegistryCopy.readOnly();

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
        Set<GraphQLType> additionalTypes = schemaGeneratorHelper.buildAdditionalTypes(buildCtx);

        // Set field visibility on code registry
        buildCtx.getCodeRegistry().fieldVisibility(buildCtx.getWiring().getFieldVisibility());

        // Build the code registry
        GraphQLCodeRegistry codeRegistry = buildCtx.getCodeRegistry().build();

        // Extract operation types by name from built types
        Set<GraphQLType> allBuiltTypes = buildCtx.getTypes();

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
        fastBuilder.additionalTypes(allBuiltTypes);
        fastBuilder.additionalTypes(additionalTypes);

        // Add all directive definitions
        fastBuilder.additionalDirectives(additionalDirectives);

        // Add schema description if present
        typeRegistry.schemaDefinition().ifPresent(schemaDefinition -> {
            String description = buildDescription(buildCtx, schemaDefinition, schemaDefinition.getDescription());
            fastBuilder.description(description);
        });

        // Add schema definition
        fastBuilder.definition(typeRegistry.schemaDefinition().orElse(null));

        // Disable validation for performance
        fastBuilder.withValidation(false);

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

    private GraphQLObjectType findOperationType(Set<GraphQLType> types, String typeName) {
        for (GraphQLType type : types) {
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
