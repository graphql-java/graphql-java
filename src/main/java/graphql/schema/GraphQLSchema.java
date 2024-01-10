package graphql.schema;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.introspection.Introspection;
import graphql.language.SchemaDefinition;
import graphql.language.SchemaExtensionDefinition;
import graphql.schema.impl.GraphQLTypeCollectingVisitor;
import graphql.schema.impl.SchemaUtil;
import graphql.schema.validation.InvalidSchemaException;
import graphql.schema.validation.SchemaValidationError;
import graphql.schema.validation.SchemaValidator;
import graphql.schema.visibility.GraphqlFieldVisibility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.collect.ImmutableKit.map;
import static graphql.collect.ImmutableKit.nonNullCopyOf;
import static graphql.schema.GraphqlTypeComparators.byNameAsc;
import static graphql.schema.GraphqlTypeComparators.sortTypes;
import static java.util.Arrays.asList;

/**
 * The schema represents the combined type system of the graphql engine.  This is how the engine knows
 * what graphql queries represent what data.
 * <p>
 * See <a href="https://graphql.org/learn/schema/#type-language">https://graphql.org/learn/schema/#type-language</a> for more details
 */
@PublicApi
public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final GraphQLObjectType mutationType;
    private final GraphQLObjectType subscriptionType;
    private final GraphQLObjectType introspectionSchemaType;
    private final ImmutableSet<GraphQLType> additionalTypes;
    private final GraphQLFieldDefinition introspectionSchemaField;
    private final GraphQLFieldDefinition introspectionTypeField;
    // we don't allow modification of "__typename" - it's a scalar
    private final GraphQLFieldDefinition __typename = Introspection.TypeNameMetaFieldDef;
    private final DirectivesUtil.DirectivesHolder directiveDefinitionsHolder;
    private final DirectivesUtil.DirectivesHolder schemaAppliedDirectivesHolder;

    private final SchemaDefinition definition;
    private final ImmutableList<SchemaExtensionDefinition> extensionDefinitions;
    private final String description;
    private final GraphQLCodeRegistry codeRegistry;

    private final ImmutableMap<String, GraphQLNamedType> typeMap;
    private final ImmutableMap<String, ImmutableList<GraphQLObjectType>> interfaceNameToObjectTypes;
    private final ImmutableMap<String, ImmutableList<String>> interfaceNameToObjectTypeNames;

    /*
     * This constructs partial GraphQL schema object which has has the schema (query / mutation / subscription) trees
     * in it but it does not have the collected types, code registry nor the type references replaced
     *
     * But it can be traversed to discover all that and filled out later via another constructor.
     *
     */
    @Internal
    private GraphQLSchema(Builder builder) {
        assertNotNull(builder.additionalTypes, () -> "additionalTypes can't be null");
        assertNotNull(builder.queryType, () -> "queryType can't be null");
        assertNotNull(builder.additionalDirectives, () -> "directives can't be null");
        assertNotNull(builder.codeRegistry, () -> "codeRegistry can't be null");

        this.queryType = builder.queryType;
        this.mutationType = builder.mutationType;
        this.subscriptionType = builder.subscriptionType;
        this.additionalTypes = ImmutableSet.copyOf(builder.additionalTypes);
        this.introspectionSchemaType = builder.introspectionSchemaType;
        this.introspectionSchemaField = Introspection.buildSchemaField(builder.introspectionSchemaType);
        this.introspectionTypeField = Introspection.buildTypeField(builder.introspectionSchemaType);
        this.directiveDefinitionsHolder = new DirectivesUtil.DirectivesHolder(builder.additionalDirectives, emptyList());
        this.schemaAppliedDirectivesHolder = new DirectivesUtil.DirectivesHolder(builder.schemaDirectives, builder.schemaAppliedDirectives);
        this.definition = builder.definition;
        this.extensionDefinitions = nonNullCopyOf(builder.extensionDefinitions);
        this.description = builder.description;

        this.codeRegistry = null;
        this.typeMap = ImmutableKit.emptyMap();
        this.interfaceNameToObjectTypes = ImmutableKit.emptyMap();
        this.interfaceNameToObjectTypeNames = ImmutableKit.emptyMap();
    }

    /*
     * This constructs a full fledged graphql schema object that has not yet had its type references replaced
     * but it's otherwise complete
     */
    @Internal
    public GraphQLSchema(GraphQLSchema existingSchema,
                         GraphQLCodeRegistry codeRegistry,
                         ImmutableMap<String, GraphQLNamedType> typeMap,
                         ImmutableMap<String, ImmutableList<GraphQLObjectType>> interfaceNameToObjectTypes
    ) {
        assertNotNull(codeRegistry, () -> "codeRegistry can't be null");

        this.queryType = existingSchema.queryType;
        this.mutationType = existingSchema.mutationType;
        this.subscriptionType = existingSchema.subscriptionType;
        this.additionalTypes = ImmutableSet.copyOf(existingSchema.additionalTypes);
        this.introspectionSchemaType = existingSchema.introspectionSchemaType;
        this.introspectionSchemaField = Introspection.buildSchemaField(existingSchema.introspectionSchemaType);
        this.introspectionTypeField = Introspection.buildTypeField(existingSchema.introspectionSchemaType);
        this.directiveDefinitionsHolder = existingSchema.directiveDefinitionsHolder;
        this.schemaAppliedDirectivesHolder = existingSchema.schemaAppliedDirectivesHolder;
        this.definition = existingSchema.definition;
        this.extensionDefinitions = existingSchema.extensionDefinitions;
        this.description = existingSchema.description;
        this.codeRegistry = codeRegistry;
        this.typeMap = typeMap;
        this.interfaceNameToObjectTypes = interfaceNameToObjectTypes;
        this.interfaceNameToObjectTypeNames = buildInterfacesToObjectName(interfaceNameToObjectTypes);
    }

    /*
     * a constructor aimed at the simple builder - the type tree can be taken as is!
     */
    @Internal
    public GraphQLSchema(BuilderWithoutTypes builder) {
        assertNotNull(builder.codeRegistry, () -> "codeRegistry can't be null");

        GraphQLSchema existingSchema = builder.existingSchema;

        this.queryType = existingSchema.queryType;
        this.mutationType = existingSchema.mutationType;
        this.subscriptionType = existingSchema.subscriptionType;
        this.additionalTypes = existingSchema.additionalTypes;
        this.introspectionSchemaType = existingSchema.introspectionSchemaType;
        this.introspectionSchemaField = existingSchema.introspectionSchemaField;
        this.introspectionTypeField = existingSchema.introspectionTypeField;
        this.directiveDefinitionsHolder = existingSchema.directiveDefinitionsHolder;
        this.schemaAppliedDirectivesHolder = existingSchema.schemaAppliedDirectivesHolder;
        this.definition = existingSchema.definition;
        this.extensionDefinitions = existingSchema.extensionDefinitions;
        this.typeMap = existingSchema.typeMap;
        this.interfaceNameToObjectTypes = existingSchema.interfaceNameToObjectTypes;
        this.interfaceNameToObjectTypeNames = existingSchema.interfaceNameToObjectTypeNames;

        this.description = builder.description;
        this.codeRegistry = builder.codeRegistry;
    }

    private static GraphQLDirective[] schemaDirectivesArray(GraphQLSchema existingSchema) {
        return existingSchema.schemaAppliedDirectivesHolder.getDirectives().toArray(new GraphQLDirective[0]);
    }

    private static GraphQLAppliedDirective[] schemaAppliedDirectivesArray(GraphQLSchema existingSchema) {
        return existingSchema.schemaAppliedDirectivesHolder.getAppliedDirectives().toArray(new GraphQLAppliedDirective[0]);
    }

    private static List<GraphQLNamedType> getAllTypesAsList(ImmutableMap<String, GraphQLNamedType> typeMap) {
        return sortTypes(byNameAsc(), typeMap.values());
    }

    private static ImmutableMap<String, ImmutableList<GraphQLObjectType>> buildInterfacesToObjectTypes(Map<String, List<GraphQLObjectType>> groupImplementations) {
        ImmutableMap.Builder<String, ImmutableList<GraphQLObjectType>> map = ImmutableMap.builder();
        for (Map.Entry<String, List<GraphQLObjectType>> e : groupImplementations.entrySet()) {
            ImmutableList<GraphQLObjectType> sortedObjectTypes = ImmutableList.copyOf(sortTypes(byNameAsc(), e.getValue()));
            map.put(e.getKey(), sortedObjectTypes);
        }
        return map.build();
    }

    private static ImmutableMap<String, ImmutableList<String>> buildInterfacesToObjectName(ImmutableMap<String, ImmutableList<GraphQLObjectType>> byInterface) {
        ImmutableMap.Builder<String, ImmutableList<String>> map = ImmutableMap.builder();
        for (Map.Entry<String, ImmutableList<GraphQLObjectType>> e : byInterface.entrySet()) {
            ImmutableList<String> objectTypeNames = map(e.getValue(), GraphQLObjectType::getName);
            map.put(e.getKey(), objectTypeNames);
        }
        return map.build();
    }

    public GraphQLCodeRegistry getCodeRegistry() {
        return codeRegistry;
    }

    /**
     * @return the special system field called "__schema"
     */
    public GraphQLFieldDefinition getIntrospectionSchemaFieldDefinition() {
        return introspectionSchemaField;
    }

    /**
     * @return the special system field called "__type"
     */
    public GraphQLFieldDefinition getIntrospectionTypeFieldDefinition() {
        return introspectionTypeField;
    }

    /**
     * @return the special system field called "__typename"
     */
    public GraphQLFieldDefinition getIntrospectionTypenameFieldDefinition() {
        return __typename;
    }

    public GraphQLObjectType getIntrospectionSchemaType() {
        return introspectionSchemaType;
    }

    public Set<GraphQLType> getAdditionalTypes() {
        return additionalTypes;
    }

    /**
     * Gets the named type from the schema or null if it's not present
     *
     * @param typeName the name of the type to retrieve
     *
     * @return the type
     */
    public @Nullable GraphQLType getType(@NotNull String typeName) {
        return typeMap.get(typeName);
    }

    /**
     * All types with the provided names.
     * throws {@link graphql.AssertException} when a type name could not be resolved
     *
     * @param typeNames the type names to get
     * @param <T>       for two
     *
     * @return The List of resolved types.
     */
    @SuppressWarnings("unchecked")
    public <T extends GraphQLType> List<T> getTypes(Collection<String> typeNames) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        for (String typeName : typeNames) {
            builder.add((T) assertNotNull(typeMap.get(typeName), () -> String.format("No type found for name %s", typeName)));
        }
        return builder.build();
    }

    /**
     * Gets the named type from the schema or null if it's not present.
     *
     * Warning - you are inviting class cast errors if the types are not what you expect.
     *
     * @param typeName the name of the type to retrieve
     * @param <T>      for two
     *
     * @return the type cast to the target type.
     */
    public <T extends GraphQLType> T getTypeAs(String typeName) {
        //noinspection unchecked
        return (T) typeMap.get(typeName);
    }

    /**
     * Returns true if the schema contains a type with the specified name
     *
     * @param typeName the name of the type to check
     *
     * @return true if there is a type with the specified name
     */
    public boolean containsType(String typeName) {
        return typeMap.containsKey(typeName);
    }

    /**
     * Called to return a named {@link graphql.schema.GraphQLObjectType} from the schema
     *
     * @param typeName the name of the type
     *
     * @return a graphql object type or null if there is one
     *
     * @throws graphql.GraphQLException if the type is NOT a object type
     */
    public GraphQLObjectType getObjectType(String typeName) {
        GraphQLType graphQLType = typeMap.get(typeName);
        if (graphQLType != null) {
            assertTrue(graphQLType instanceof GraphQLObjectType,
                    () -> String.format("You have asked for named object type '%s' but it's not an object type but rather a '%s'", typeName, graphQLType.getClass().getName()));
        }
        return (GraphQLObjectType) graphQLType;
    }

    /**
     * Returns a {@link GraphQLFieldDefinition} as the specified co-ordinates or null
     * if it does not exist
     *
     * @param fieldCoordinates the field co-ordinates
     *
     * @return the field or null if it does not exist
     */
    public GraphQLFieldDefinition getFieldDefinition(FieldCoordinates fieldCoordinates) {
        String fieldName = fieldCoordinates.getFieldName();
        if (fieldCoordinates.isSystemCoordinates()) {
            if (fieldName.equals(this.getIntrospectionSchemaFieldDefinition().getName())) {
                return this.getIntrospectionSchemaFieldDefinition();
            }
            if (fieldName.equals(this.getIntrospectionTypeFieldDefinition().getName())) {
                return this.getIntrospectionTypeFieldDefinition();
            }
            if (fieldName.equals(this.getIntrospectionTypenameFieldDefinition().getName())) {
                return this.getIntrospectionTypenameFieldDefinition();
            }
            return Assert.assertShouldNeverHappen("The system field name %s is unknown", fieldName);
        }
        String typeName = fieldCoordinates.getTypeName();
        GraphQLType graphQLType = getType(typeName);
        if (graphQLType != null) {
            assertTrue(graphQLType instanceof GraphQLFieldsContainer,
                    () -> String.format("You have asked for named type '%s' but it's not GraphQLFieldsContainer but rather a '%s'", typeName, graphQLType.getClass().getName()));
            return ((GraphQLFieldsContainer) graphQLType).getFieldDefinition(fieldName);
        }
        return null;
    }

    /**
     * @return all the named types in the scheme as a map from name to named type
     */
    public Map<String, GraphQLNamedType> getTypeMap() {
        return typeMap;
    }

    /**
     * This returns all the {@link GraphQLNamedType} named types in th schema
     *
     * @return all the {@link GraphQLNamedType} types in the schema
     */
    public List<GraphQLNamedType> getAllTypesAsList() {
        return getAllTypesAsList(typeMap);
    }

    /**
     * This returns all the top level {@link GraphQLNamedSchemaElement} named types and directives
     * in the schema
     *
     * @return all the top level {@link GraphQLNamedSchemaElement} types and directives in the schema
     */
    public List<GraphQLNamedSchemaElement> getAllElementsAsList() {
        List<GraphQLNamedSchemaElement> list = new ArrayList<>();
        list.addAll(getDirectives());
        list.addAll(getAllTypesAsList());
        return list;
    }

    /**
     * This will return the list of {@link graphql.schema.GraphQLObjectType} types that implement the given
     * interface type.
     *
     * @param type interface type to obtain implementations of.
     *
     * @return list of types implementing provided interface
     */
    public List<GraphQLObjectType> getImplementations(GraphQLInterfaceType type) {
        return interfaceNameToObjectTypes.getOrDefault(type.getName(), emptyList());
    }

    /**
     * Returns true if a specified concrete type is a possible type of a provided abstract type.
     * If the provided abstract type is:
     * - an interface, it checks whether the concrete type is one of its implementations.
     * - a union, it checks whether the concrete type is one of its possible types.
     *
     * @param abstractType abstract type either interface or union
     * @param concreteType concrete type
     *
     * @return true if possible type, false otherwise.
     */
    public boolean isPossibleType(GraphQLNamedType abstractType, GraphQLObjectType concreteType) {
        if (abstractType instanceof GraphQLInterfaceType) {
            ImmutableList<String> objectNames = this.interfaceNameToObjectTypeNames.getOrDefault(abstractType.getName(), emptyList());
            return objectNames.contains(concreteType.getName());
        } else if (abstractType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) abstractType).isPossibleType(concreteType);
        }
        return assertShouldNeverHappen("Unsupported abstract type %s. Abstract types supported are Union and Interface.", abstractType.getName());
    }

    /**
     * @return the Query type of the schema
     */
    public GraphQLObjectType getQueryType() {
        return queryType;
    }

    /**
     * @return the Mutation type of the schema of null if there is not one
     */
    public GraphQLObjectType getMutationType() {
        return mutationType;
    }

    /**
     * @return the Subscription type of the schema of null if there is not one
     */
    public GraphQLObjectType getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * @return the field visibility
     *
     * @deprecated use {@link GraphQLCodeRegistry#getFieldVisibility()} instead
     */
    @Deprecated(since = "2018-12-03")
    public GraphqlFieldVisibility getFieldVisibility() {
        return codeRegistry.getFieldVisibility();
    }

    /**
     * This returns the list of directives definitions that are associated with this schema object including
     * built in ones.
     *
     * @return a list of directives
     */
    public List<GraphQLDirective> getDirectives() {
        return directiveDefinitionsHolder.getDirectives();
    }

    /**
     * @return a map of non repeatable directives by directive name
     */
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directiveDefinitionsHolder.getDirectivesByName();
    }

    /**
     * Returns a named directive that (for legacy reasons) will be only in the set of non repeatable directives
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is not one with that name
     */
    public GraphQLDirective getDirective(String directiveName) {
        return directiveDefinitionsHolder.getDirective(directiveName);
    }



    /**
     * This returns the list of directives that have been explicitly applied to the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a list of directives
     *
     * @deprecated Use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    public List<GraphQLDirective> getSchemaDirectives() {
        return schemaAppliedDirectivesHolder.getDirectives();
    }

    /**
     * This returns a map of non-repeatable directives that have been explicitly applied to the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a map of directives
     *
     * @deprecated Use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    public Map<String, GraphQLDirective> getSchemaDirectiveByName() {
        return schemaAppliedDirectivesHolder.getDirectivesByName();
    }

    /**
     * This returns a map of non-repeatable and repeatable directives that have been explicitly applied to the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a map  of directives
     *
     * @deprecated Use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    public Map<String, List<GraphQLDirective>> getAllSchemaDirectivesByName() {
        return schemaAppliedDirectivesHolder.getAllDirectivesByName();
    }

    /**
     * This returns the named directive that have been explicitly applied to the
     * schema object.  Note that {@link graphql.schema.GraphQLDirectiveContainer#getDirective(String)} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @param directiveName the name of the directive
     *
     * @return a named directive
     *
     * @deprecated Use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    public GraphQLDirective getSchemaDirective(String directiveName) {
        return schemaAppliedDirectivesHolder.getDirective(directiveName);
    }

    /**
     * This returns the named directives that have been explicitly applied to the
     * schema object.
     *
     * @param directiveName the name of the directive
     *
     * @return A list of repeated applied directives
     *
     * @deprecated Use the {@link GraphQLAppliedDirective} methods instead
     */
    @Deprecated(since = "2022-02-24")
    public List<GraphQLDirective> getSchemaDirectives(String directiveName) {
        return schemaAppliedDirectivesHolder.getDirectives(directiveName);
    }

    /**
     * This returns the list of directives that have been explicitly applied to the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a map of directives
     */
    public List<GraphQLAppliedDirective> getSchemaAppliedDirectives() {
        return schemaAppliedDirectivesHolder.getAppliedDirectives();
    }

    /**
     * This returns a map of non-repeatable and repeatable directives that have been explicitly applied to the
     * schema object.  Note that {@link #getDirectives()} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @return a map of all schema directives by directive name
     */
    public Map<String, List<GraphQLAppliedDirective>> getAllSchemaAppliedDirectivesByName() {
        return schemaAppliedDirectivesHolder.getAllAppliedDirectivesByName();
    }

    /**
     * This returns the named directive that have been explicitly applied to the
     * schema object.  Note that {@link graphql.schema.GraphQLDirectiveContainer#getDirective(String)} will return
     * directives for all schema elements, whereas this is just for the schema
     * element itself
     *
     * @param directiveName the name of the directive
     *
     * @return a named directive
     */
    public GraphQLAppliedDirective getSchemaAppliedDirective(String directiveName) {
        return schemaAppliedDirectivesHolder.getAppliedDirective(directiveName);
    }

    public List<GraphQLAppliedDirective> getSchemaAppliedDirectives(String directiveName) {
        return schemaAppliedDirectivesHolder.getAppliedDirectives(directiveName);
    }

    @Nullable
    public SchemaDefinition getDefinition() {
        return definition;
    }

    public List<SchemaExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    public boolean isSupportingMutations() {
        return mutationType != null;
    }

    public boolean isSupportingSubscriptions() {
        return subscriptionType != null;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * This helps you transform the current GraphQLSchema object into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new GraphQLSchema object based on calling built on that builder
     */
    public GraphQLSchema transform(Consumer<Builder> builderConsumer) {
        Builder builder = newSchema(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * This helps you transform the current GraphQLSchema object into another one by using a builder that only allows you to change
     * simple values and does not involve changing the complex schema type graph.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new GraphQLSchema object based on calling built on that builder
     */
    public GraphQLSchema transformWithoutTypes(Consumer<BuilderWithoutTypes> builderConsumer) {
        BuilderWithoutTypes builder = new BuilderWithoutTypes(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * @return a new schema builder
     */
    public static Builder newSchema() {
        return new Builder();
    }

    /**
     * This allows you to build a schema from an existing schema.  It copies everything from the existing
     * schema and then allows you to replace them.
     *
     * @param existingSchema the existing schema
     *
     * @return a new schema builder
     */
    public static Builder newSchema(GraphQLSchema existingSchema) {
        return new Builder()
                .query(existingSchema.getQueryType())
                .mutation(existingSchema.getMutationType())
                .subscription(existingSchema.getSubscriptionType())
                .introspectionSchemaType(existingSchema.getIntrospectionSchemaType())
                .codeRegistry(existingSchema.getCodeRegistry())
                .clearAdditionalTypes()
                .clearDirectives()
                .additionalDirectives(new LinkedHashSet<>(existingSchema.getDirectives()))
                .clearSchemaDirectives()
                .withSchemaDirectives(schemaDirectivesArray(existingSchema))
                .withSchemaAppliedDirectives(schemaAppliedDirectivesArray(existingSchema))
                .additionalTypes(existingSchema.additionalTypes)
                .description(existingSchema.getDescription());
    }

    public static class BuilderWithoutTypes {
        private GraphQLCodeRegistry codeRegistry;
        private String description;
        private final GraphQLSchema existingSchema;

        private BuilderWithoutTypes(GraphQLSchema existingSchema) {
            this.existingSchema = existingSchema;
            this.codeRegistry = existingSchema.codeRegistry;
            this.description = existingSchema.description;
        }

        public BuilderWithoutTypes codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = Assert.assertNotNull(codeRegistry);
            return this;
        }

        public BuilderWithoutTypes codeRegistry(GraphQLCodeRegistry.Builder codeRegistryBuilder) {
            return codeRegistry(codeRegistryBuilder.build());
        }

        public BuilderWithoutTypes description(String description) {
            this.description = description;
            return this;
        }

        public GraphQLSchema build() {
            return new GraphQLSchema(this);
        }
    }

    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;
        private GraphQLObjectType introspectionSchemaType = Introspection.__Schema;
        private GraphQLObjectType subscriptionType;
        private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
        private SchemaDefinition definition;
        private List<SchemaExtensionDefinition> extensionDefinitions;
        private String description;

        // we default these in
        private final Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>(
                asList(Directives.IncludeDirective, Directives.SkipDirective)
        );
        private final Set<GraphQLType> additionalTypes = new LinkedHashSet<>();
        private final List<GraphQLDirective> schemaDirectives = new ArrayList<>();
        private final List<GraphQLAppliedDirective> schemaAppliedDirectives = new ArrayList<>();

        public Builder query(GraphQLObjectType.Builder builder) {
            return query(builder.build());
        }

        public Builder query(GraphQLObjectType queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder mutation(GraphQLObjectType.Builder builder) {
            return mutation(builder.build());
        }

        public Builder mutation(GraphQLObjectType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder subscription(GraphQLObjectType.Builder builder) {
            return subscription(builder.build());
        }

        public Builder subscription(GraphQLObjectType subscriptionType) {
            this.subscriptionType = subscriptionType;
            return this;
        }

        /**
         * @param fieldVisibility the field visibility
         *
         * @return this builder
         *
         * @deprecated use {@link graphql.schema.GraphQLCodeRegistry.Builder#fieldVisibility(graphql.schema.visibility.GraphqlFieldVisibility)} instead
         */
        @Deprecated(since = "2018-12-03")
        public Builder fieldVisibility(GraphqlFieldVisibility fieldVisibility) {
            this.codeRegistry = this.codeRegistry.transform(builder -> builder.fieldVisibility(fieldVisibility));
            return this;
        }

        public Builder codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = codeRegistry;
            return this;
        }

        public Builder additionalTypes(Set<GraphQLType> additionalTypes) {
            this.additionalTypes.addAll(additionalTypes);
            return this;
        }

        public Builder additionalType(GraphQLType additionalType) {
            this.additionalTypes.add(additionalType);
            return this;
        }

        public Builder clearAdditionalTypes() {
            this.additionalTypes.clear();
            return this;
        }

        public Builder additionalDirectives(Set<GraphQLDirective> additionalDirectives) {
            this.additionalDirectives.addAll(additionalDirectives);
            return this;
        }

        public Builder additionalDirective(GraphQLDirective additionalDirective) {
            this.additionalDirectives.add(additionalDirective);
            return this;
        }

        public Builder clearDirectives() {
            this.additionalDirectives.clear();
            return this;
        }


        public Builder withSchemaDirectives(GraphQLDirective... directives) {
            for (GraphQLDirective directive : directives) {
                withSchemaDirective(directive);
            }
            return this;
        }

        public Builder withSchemaDirectives(Collection<? extends GraphQLDirective> directives) {
            for (GraphQLDirective directive : directives) {
                withSchemaDirective(directive);
            }
            return this;
        }

        public Builder withSchemaDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            schemaDirectives.add(directive);
            return this;
        }

        public Builder withSchemaDirective(GraphQLDirective.Builder builder) {
            return withSchemaDirective(builder.build());
        }

        public Builder withSchemaAppliedDirectives(GraphQLAppliedDirective... appliedDirectives) {
            for (GraphQLAppliedDirective directive : appliedDirectives) {
                withSchemaAppliedDirective(directive);
            }
            return this;
        }

        public Builder withSchemaAppliedDirectives(Collection<? extends GraphQLAppliedDirective> appliedDirectives) {
            for (GraphQLAppliedDirective directive : appliedDirectives) {
                withSchemaAppliedDirective(directive);
            }
            return this;
        }

        public Builder withSchemaAppliedDirective(GraphQLAppliedDirective appliedDirective) {
            assertNotNull(appliedDirective, () -> "directive can't be null");
            schemaAppliedDirectives.add(appliedDirective);
            return this;
        }

        public Builder withSchemaAppliedDirective(GraphQLAppliedDirective.Builder builder) {
            return withSchemaAppliedDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearSchemaDirectives() {
            schemaDirectives.clear();
            schemaAppliedDirectives.clear();
            return this;
        }

        public Builder definition(SchemaDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<SchemaExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder introspectionSchemaType(GraphQLObjectType introspectionSchemaType) {
            this.introspectionSchemaType = introspectionSchemaType;
            return this;
        }

        /**
         * Builds the schema
         *
         * @param additionalTypes - please don't use this anymore
         *
         * @return the built schema
         *
         * @deprecated - Use the {@link #additionalType(GraphQLType)} methods
         */
        @Deprecated(since = "2018-07-30")
        public GraphQLSchema build(Set<GraphQLType> additionalTypes) {
            return additionalTypes(additionalTypes).build();
        }

        /**
         * Builds the schema
         *
         * @param additionalTypes      - please don't use this any more
         * @param additionalDirectives - please don't use this any more
         *
         * @return the built schema
         *
         * @deprecated - Use the {@link #additionalType(GraphQLType)} and {@link #additionalDirective(GraphQLDirective)} methods
         */
        @Deprecated(since = "2018-07-30")
        public GraphQLSchema build(Set<GraphQLType> additionalTypes, Set<GraphQLDirective> additionalDirectives) {
            return additionalTypes(additionalTypes).additionalDirectives(additionalDirectives).build();
        }

        /**
         * Builds the schema
         *
         * @return the built schema
         */
        public GraphQLSchema build() {
            return buildImpl();
        }

        private GraphQLSchema buildImpl() {
            assertNotNull(additionalTypes, () -> "additionalTypes can't be null");
            assertNotNull(additionalDirectives, () -> "additionalDirectives can't be null");

            // schemas built via the schema generator have the deprecated directive BUT we want it present for hand built
            // schemas - it's inherently part of the spec!
            if (additionalDirectives.stream().noneMatch(d -> d.getName().equals(Directives.DeprecatedDirective.getName()))) {
                additionalDirectives.add(Directives.DeprecatedDirective);
            }

            if (additionalDirectives.stream().noneMatch(d -> d.getName().equals(Directives.SpecifiedByDirective.getName()))) {
                additionalDirectives.add(Directives.SpecifiedByDirective);
            }
            if (additionalDirectives.stream().noneMatch(d -> d.getName().equals(Directives.OneOfDirective.getName()))) {
                additionalDirectives.add(Directives.OneOfDirective);
            }

            // quick build - no traversing
            final GraphQLSchema partiallyBuiltSchema = new GraphQLSchema(this);

            GraphQLCodeRegistry.Builder extractedDataFetchers = GraphQLCodeRegistry.newCodeRegistry(codeRegistry);
            CodeRegistryVisitor codeRegistryVisitor = new CodeRegistryVisitor(extractedDataFetchers);
            GraphQLTypeCollectingVisitor typeCollectingVisitor = new GraphQLTypeCollectingVisitor();
            SchemaUtil.visitPartiallySchema(partiallyBuiltSchema, codeRegistryVisitor, typeCollectingVisitor);

            codeRegistry = extractedDataFetchers.build();
            ImmutableMap<String, GraphQLNamedType> allTypes = typeCollectingVisitor.getResult();
            List<GraphQLNamedType> allTypesAsList = getAllTypesAsList(allTypes);

            ImmutableMap<String, List<GraphQLObjectType>> groupedImplementations = SchemaUtil.groupInterfaceImplementationsByName(allTypesAsList);
            ImmutableMap<String, ImmutableList<GraphQLObjectType>> interfaceNameToObjectTypes = buildInterfacesToObjectTypes(groupedImplementations);

            // this is now build however its contained types are still to be mutated by type reference replacement
            final GraphQLSchema finalSchema = new GraphQLSchema(partiallyBuiltSchema, codeRegistry, allTypes, interfaceNameToObjectTypes);
            SchemaUtil.replaceTypeReferences(finalSchema);
            return validateSchema(finalSchema);
        }

        private GraphQLSchema validateSchema(GraphQLSchema graphQLSchema) {
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (errors.size() > 0) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }
    }
}
