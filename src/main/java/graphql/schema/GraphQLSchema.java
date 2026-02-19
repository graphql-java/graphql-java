package graphql.schema;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.AssertException;
import graphql.Directives;
import graphql.Scalars;
import graphql.DirectivesUtil;
import graphql.ExperimentalApi;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
import static java.util.Collections.singletonList;

/**
 * The schema represents the combined type system of the graphql engine.  This is how the engine knows
 * what graphql queries represent what data.
 * <p>
 * See <a href="https://graphql.org/learn/schema/#type-language">https://graphql.org/learn/schema/#type-language</a> for more details
 */
@PublicApi
@NullMarked
public class GraphQLSchema {

    private final GraphQLObjectType queryType;
    private final @Nullable GraphQLObjectType mutationType;
    private final @Nullable GraphQLObjectType subscriptionType;
    private final GraphQLObjectType introspectionSchemaType;
    private final ImmutableSet<GraphQLNamedType> additionalTypes;
    private final GraphQLFieldDefinition introspectionSchemaField;
    private final GraphQLFieldDefinition introspectionTypeField;
    // we don't allow modification of "__typename" - it's a scalar
    private final GraphQLFieldDefinition __typename = Introspection.TypeNameMetaFieldDef;
    private final DirectivesUtil.DirectivesHolder directiveDefinitionsHolder;
    private final DirectivesUtil.DirectivesHolder schemaAppliedDirectivesHolder;

    private final @Nullable SchemaDefinition definition;
    private final ImmutableList<SchemaExtensionDefinition> extensionDefinitions;
    private final @Nullable String description;
    private final GraphQLCodeRegistry codeRegistry;

    private final ImmutableMap<String, GraphQLNamedType> typeMap;
    private final ImmutableMap<String, ImmutableList<GraphQLObjectType>> interfaceNameToObjectTypes;
    private final ImmutableMap<String, ImmutableList<String>> interfaceNameToObjectTypeNames;

    /*
     * This constructs partial GraphQL schema object which has the schema (query / mutation / subscription) trees
     * in it but it does not have the collected types, the correct code registry nor the type references replaced
     *
     * But it can be traversed to discover all that and filled out later via another constructor.
     *
     */
    @Internal
    private GraphQLSchema(Builder builder) {
        assertNotNull(builder.additionalTypes, "additionalTypes can't be null");
        assertNotNull(builder.queryType, "queryType can't be null");
        assertNotNull(builder.additionalDirectives, "directives can't be null");
        assertNotNull(builder.codeRegistry, "codeRegistry can't be null");

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

        this.codeRegistry = builder.codeRegistry;
        this.typeMap = ImmutableKit.emptyMap();
        this.interfaceNameToObjectTypes = ImmutableKit.emptyMap();
        this.interfaceNameToObjectTypeNames = ImmutableKit.emptyMap();
    }

    /*
     * This constructs a fully fledged graphql schema object that has not yet had its type references replaced
     * but it's otherwise complete
     */
    @Internal
    public GraphQLSchema(GraphQLSchema existingSchema,
                         GraphQLCodeRegistry codeRegistry,
                         ImmutableMap<String, GraphQLNamedType> typeMap,
                         ImmutableMap<String, ImmutableList<GraphQLObjectType>> interfaceNameToObjectTypes
    ) {
        assertNotNull(codeRegistry, "codeRegistry can't be null");

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
        assertNotNull(builder.codeRegistry, "codeRegistry can't be null");

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

    /**
     * Private constructor for FastBuilder that copies data from the builder
     * and converts mutable collections to immutable ones.
     */
    @Internal
    private GraphQLSchema(FastBuilder fastBuilder) {
        // Build immutable collections from FastBuilder's mutable state
        ImmutableMap<String, GraphQLNamedType> finalTypeMap = ImmutableMap.copyOf(fastBuilder.typeMap);
        ImmutableList<GraphQLDirective> finalDirectives = ImmutableList.copyOf(fastBuilder.directiveMap.values());

        // Get interface-to-object-type-names map from collector (already sorted)
        ImmutableMap<String, ImmutableList<String>> finalInterfaceNameMap =
                fastBuilder.shallowTypeRefCollector.getInterfaceNameToObjectTypeNames();

        // Build interface-to-object-types map by looking up types in typeMap
        ImmutableMap.Builder<String, ImmutableList<GraphQLObjectType>> interfaceMapBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ImmutableList<String>> entry : finalInterfaceNameMap.entrySet()) {
            ImmutableList<GraphQLObjectType> objectTypes = map(entry.getValue(),
                    name -> (GraphQLObjectType) assertNotNull(finalTypeMap.get(name)));
            interfaceMapBuilder.put(entry.getKey(), objectTypes);
        }
        ImmutableMap<String, ImmutableList<GraphQLObjectType>> finalInterfaceMap = interfaceMapBuilder.build();

        // Initialize all fields
        this.queryType = fastBuilder.queryType;
        this.mutationType = fastBuilder.mutationType;
        this.subscriptionType = fastBuilder.subscriptionType;
        this.introspectionSchemaType = fastBuilder.introspectionSchemaType;
        // Compute additionalTypes as all types minus root types.
        // Note: Unlike the standard Builder which computes only "detached" types (types not
        // reachable from roots), FastBuilder includes ALL non-root types in additionalTypes.
        // This is a semantic difference but does not affect schema traversal or correctness.
        Set<String> rootTypeNames = new LinkedHashSet<>();
        rootTypeNames.add(fastBuilder.queryType.getName());
        if (fastBuilder.mutationType != null) {
            rootTypeNames.add(fastBuilder.mutationType.getName());
        }
        if (fastBuilder.subscriptionType != null) {
            rootTypeNames.add(fastBuilder.subscriptionType.getName());
        }
        this.additionalTypes = finalTypeMap.values().stream()
                .filter(type -> !rootTypeNames.contains(type.getName()))
                .collect(ImmutableSet.toImmutableSet());
        this.introspectionSchemaField = Introspection.buildSchemaField(fastBuilder.introspectionSchemaType);
        this.introspectionTypeField = Introspection.buildTypeField(fastBuilder.introspectionSchemaType);
        this.directiveDefinitionsHolder = new DirectivesUtil.DirectivesHolder(finalDirectives, emptyList());
        this.schemaAppliedDirectivesHolder = new DirectivesUtil.DirectivesHolder(
                ImmutableList.copyOf(fastBuilder.schemaDirectives),
                ImmutableList.copyOf(fastBuilder.schemaAppliedDirectives));
        this.definition = fastBuilder.definition;
        this.extensionDefinitions = nonNullCopyOf(fastBuilder.extensionDefinitions);
        this.description = fastBuilder.description;
        this.codeRegistry = fastBuilder.codeRegistryBuilder.build();
        this.typeMap = finalTypeMap;
        this.interfaceNameToObjectTypes = finalInterfaceMap;
        this.interfaceNameToObjectTypeNames = finalInterfaceNameMap;
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

    /**
     * Returns the set of "additional types" that were provided when building the schema.
     * <p>
     * During schema construction, types are discovered by traversing the schema from multiple roots:
     * <ul>
     *     <li>Root operation types (Query, Mutation, Subscription)</li>
     *     <li>Directive argument types</li>
     *     <li>Introspection types</li>
     *     <li>Types explicitly added via {@link Builder#additionalType(GraphQLNamedType)}</li>
     * </ul>
     * <p>
     * Additional types are types that are not reachable via any of the automatic traversal paths
     * but still need to be part of the schema. The most common use case is for interface
     * implementations that are not directly referenced elsewhere.
     * <p>
     * <b>Types that do NOT need to be added as additional types:</b>
     * <ul>
     *     <li>Types reachable from Query, Mutation, or Subscription fields</li>
     *     <li>Types used as directive arguments (these are discovered via directive traversal)</li>
     * </ul>
     * <p>
     * <b>When additional types ARE typically needed:</b>
     * <ul>
     *     <li><b>Interface implementations:</b> When an interface is used as a field's return type,
     *     implementing object types are not automatically discovered because interfaces do not
     *     reference their implementors. These need to be added so they can be resolved at runtime
     *     and appear in introspection.</li>
     *     <li><b>SDL-defined schemas:</b> When building from SDL, the {@link graphql.schema.idl.SchemaGenerator}
     *     automatically detects types not connected to any root and adds them as additional types.</li>
     *     <li><b>Programmatic schemas with type references:</b> When using {@link GraphQLTypeReference}
     *     to break circular dependencies, the actual type implementations may need to be provided
     *     as additional types.</li>
     * </ul>
     * <p>
     * <b>Example - Interface implementation not directly referenced:</b>
     * <pre>{@code
     * // Given this schema:
     * // type Query { node: Node }
     * // interface Node { id: ID! }
     * // type User implements Node { id: ID!, name: String }
     * //
     * // User is not directly referenced from Query, so it needs to be added:
     * GraphQLSchema.newSchema()
     *     .query(queryType)
     *     .additionalType(GraphQLObjectType.newObject().name("User")...)
     *     .build();
     * }</pre>
     * <p>
     * <b>Note:</b> There are no restrictions on what types can be added via this mechanism.
     * Types that are already reachable from other roots can also be added without causing
     * errors - they will simply be present in both the type map (via traversal) and this set.
     * After schema construction, use {@link #getTypeMap()} or {@link #getAllTypesAsList()} to get
     * all types in the schema regardless of how they were discovered.
     * <p>
     * <b>Note on FastBuilder:</b> When a schema is constructed using {@link FastBuilder},
     * this method returns ALL types in the schema except the root operation types (Query,
     * Mutation, Subscription). This differs from schemas built with the standard
     * {@link Builder}, which returns only types not reachable from the root types.
     * This semantic difference does not affect schema traversal or correctness, as both
     * approaches ensure all types are properly discoverable.
     *
     * @return an immutable set of types that were explicitly added as additional types
     *
     * @see Builder#additionalType(GraphQLNamedType)
     * @see Builder#additionalTypes(Set)
     */
    public Set<GraphQLNamedType> getAdditionalTypes() {
        return additionalTypes;
    }

    /**
     * Gets the named type from the schema or null if it's not present
     *
     * @param typeName the name of the type to retrieve
     *
     * @return the type
     */
    public @Nullable GraphQLType getType(String typeName) {
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
            builder.add((T) assertNotNull(typeMap.get(typeName), "No type found for name %s", typeName));
        }
        return builder.build();
    }

    /**
     * Gets the named type from the schema or null if it's not present.
     * <p>
     * Warning - you are inviting class cast errors if the types are not what you expect.
     *
     * @param typeName the name of the type to retrieve
     * @param <T>      for two
     *
     * @return the type cast to the target type.
     */
    public <T extends GraphQLType> @Nullable T getTypeAs(String typeName) {
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
     * @throws graphql.GraphQLException if the type is NOT an object type
     */
    public @Nullable GraphQLObjectType getObjectType(String typeName) {
        GraphQLType graphQLType = typeMap.get(typeName);
        if (graphQLType != null) {
            assertTrue(graphQLType instanceof GraphQLObjectType,
                    "You have asked for named object type '%s' but it's not an object type but rather a '%s'", typeName, graphQLType.getClass().getName());
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
    public @Nullable GraphQLFieldDefinition getFieldDefinition(FieldCoordinates fieldCoordinates) {
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
                    "You have asked for named type '%s' but it's not GraphQLFieldsContainer but rather a '%s'", typeName, graphQLType.getClass().getName());
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
    public @Nullable List<GraphQLObjectType> getImplementations(GraphQLInterfaceType type) {
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
    public @Nullable GraphQLObjectType getMutationType() {
        return mutationType;
    }

    /**
     * @return the Subscription type of the schema of null if there is not one
     */
    public @Nullable GraphQLObjectType getSubscriptionType() {
        return subscriptionType;
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
     * @return a map of non-repeatable directives by directive name
     */
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directiveDefinitionsHolder.getDirectivesByName();
    }

    /**
     * Returns a named directive that (for legacy reasons) will be only in the set of non-repeatable directives
     *
     * @param directiveName the name of the directive to retrieve
     *
     * @return the directive or null if there is not one with that name
     */
    public @Nullable GraphQLDirective getDirective(String directiveName) {
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
     * @return a list of directives
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
                .extensionDefinitions(existingSchema.getExtensionDefinitions())
                .introspectionSchemaType(existingSchema.getIntrospectionSchemaType())
                .codeRegistry(existingSchema.getCodeRegistry())
                .clearAdditionalTypes()
                .additionalDirectives(new LinkedHashSet<>(existingSchema.getDirectives()))
                .clearSchemaDirectives()
                .withSchemaDirectives(schemaDirectivesArray(existingSchema))
                .withSchemaAppliedDirectives(schemaAppliedDirectivesArray(existingSchema))
                .additionalTypes(existingSchema.additionalTypes)
                .description(existingSchema.getDescription());
    }

    @NullUnmarked
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

    @NullUnmarked
    public static class Builder {
        private GraphQLObjectType queryType;
        private GraphQLObjectType mutationType;
        private GraphQLObjectType introspectionSchemaType = Introspection.__Schema;
        private GraphQLObjectType subscriptionType;
        private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build();
        private SchemaDefinition definition;
        private List<SchemaExtensionDefinition> extensionDefinitions;
        private String description;

        private final Set<GraphQLDirective> additionalDirectives = new LinkedHashSet<>();
        private final Set<GraphQLNamedType> additionalTypes = new LinkedHashSet<>();
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

        public Builder codeRegistry(GraphQLCodeRegistry codeRegistry) {
            this.codeRegistry = codeRegistry;
            return this;
        }

        /**
         * Adds multiple types to the set of additional types.
         * <p>
         * Additional types are types that may not be directly reachable by traversing the schema
         * from the root operation types (Query, Mutation, Subscription), but still need to be
         * included in the schema. The most common use case is for object types that implement
         * an interface but are not directly referenced as field return types.
         * <p>
         * <b>Example - Adding interface implementations:</b>
         * <pre>{@code
         * // If Node interface is used but User/Post types aren't directly referenced:
         * builder.additionalTypes(Set.of(
         *     GraphQLObjectType.newObject().name("User").withInterface(nodeInterface)...,
         *     GraphQLObjectType.newObject().name("Post").withInterface(nodeInterface)...
         * ));
         * }</pre>
         * <p>
         * <b>Note:</b> There are no restrictions on what types can be added. Types already
         * reachable from root operations can be added without causing errors - they will
         * simply exist in both the traversed type map and this set.
         *
         * @param additionalTypes the types to add
         *
         * @return this builder
         *
         * @see GraphQLSchema#getAdditionalTypes()
         */
        public Builder additionalTypes(Set<? extends GraphQLNamedType> additionalTypes) {
            this.additionalTypes.addAll(additionalTypes);
            return this;
        }

        /**
         * Adds a single type to the set of additional types.
         * <p>
         * Additional types are types that may not be directly reachable by traversing the schema
         * from the root operation types (Query, Mutation, Subscription), but still need to be
         * included in the schema. The most common use case is for object types that implement
         * an interface but are not directly referenced as field return types.
         * <p>
         * <b>Note:</b> There are no restrictions on what types can be added. Types already
         * reachable from root operations can be added without causing errors.
         *
         * @param additionalType the type to add
         *
         * @return this builder
         *
         * @see GraphQLSchema#getAdditionalTypes()
         * @see #additionalTypes(Set)
         */
        public Builder additionalType(GraphQLNamedType additionalType) {
            this.additionalTypes.add(additionalType);
            return this;
        }

        /**
         * Clears all additional types that have been added to this builder.
         * <p>
         * This is useful when transforming an existing schema and you want to
         * rebuild the additional types set from scratch.
         *
         * @return this builder
         *
         * @see #additionalType(GraphQLNamedType)
         * @see #additionalTypes(Set)
         */
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
            assertNotNull(directive, "directive can't be null");
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
            assertNotNull(appliedDirective, "directive can't be null");
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
         * @return the built schema
         */
        public GraphQLSchema build() {
            return buildImpl();
        }

        private GraphQLSchema buildImpl() {
            assertNotNull(additionalTypes, "additionalTypes can't be null");
            assertNotNull(additionalDirectives, "additionalDirectives can't be null");

            // built-in directives are always present in a schema and come first
            ensureBuiltInDirectives();

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

        private void ensureBuiltInDirectives() {
            // put built-in directives first, preserving user-supplied overrides by name
            Set<String> userDirectiveNames = new LinkedHashSet<>();
            for (GraphQLDirective d : additionalDirectives) {
                userDirectiveNames.add(d.getName());
            }
            LinkedHashSet<GraphQLDirective> ordered = new LinkedHashSet<>();
            for (GraphQLDirective builtIn : Directives.BUILT_IN_DIRECTIVES) {
                if (!userDirectiveNames.contains(builtIn.getName())) {
                    ordered.add(builtIn);
                }
            }
            ordered.addAll(additionalDirectives);
            additionalDirectives.clear();
            additionalDirectives.addAll(ordered);
        }

        private GraphQLSchema validateSchema(GraphQLSchema graphQLSchema) {
            Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(graphQLSchema);
            if (!errors.isEmpty()) {
                throw new InvalidSchemaException(errors);
            }
            return graphQLSchema;
        }

        private void addBuiltInDirective(GraphQLDirective qlDirective, Set<GraphQLDirective> additionalDirectives1) {
            if (additionalDirectives1.stream().noneMatch(d -> d.getName().equals(qlDirective.getName()))) {
                additionalDirectives1.add(qlDirective);
            }
        }
    }

    /**
     * A high-performance schema builder that avoids full-schema traversals performed by
     * {@link GraphQLSchema.Builder#build()}. This builder is significantly faster (5x+) and
     * allocates significantly less memory than the standard Builder. It is intended for
     * constructing large schemas (500+ types), especially deeply nested ones.
     *
     * <h2>When to use FastBuilder</h2>
     * <ul>
     *   <li>Building large schemas where construction time and memory are measurable concerns</li>
     *   <li>All types are known upfront and can be added explicitly via {@link #addType} or {@link #addTypes}</li>
     *   <li>The code registry is complete and available when FastBuilder is constructed</li>
     *   <li>Schema has been previously validated (e.g., in a build pipeline) and validation can be skipped</li>
     * </ul>
     *
     * <h2>When NOT to use FastBuilder</h2>
     * <ul>
     *   <li><b>Type discovery required:</b> If you rely on automatic type discovery by traversing from
     *       root types (Query/Mutation/Subscription), use the standard {@link Builder} instead.
     *       FastBuilder requires ALL types to be explicitly added.</li>
     *   <li><b>Type reuse across schemas:</b> FastBuilder mutates type objects during {@link #build()}
     *       to resolve {@link GraphQLTypeReference}s. The same type instances cannot be used to build
     *       multiple schemas. Create fresh type instances for each schema if needed.</li>
     *   <li><b>Dynamic schema construction:</b> FastBuilder does not support clearing or resetting
     *       state. Each FastBuilder instance should be used exactly once.</li>
     *   <li><b>Schema transformation:</b> For transforming existing schemas, use
     *       {@link GraphQLSchema#transform(Consumer)} or {@link Builder} instead.</li>
     * </ul>
     *
     * <h2>Key differences from standard Builder</h2>
     * <ul>
     *   <li><b>No automatic type discovery:</b> You must add ALL types explicitly, including
     *       interface implementations that would normally be discovered via traversal.</li>
     *   <li><b>Type mutation:</b> {@link GraphQLTypeReference} instances in added types are replaced
     *       in-place with actual types during build. This mutates the original type objects.</li>
     *   <li><b>additionalTypes semantic:</b> {@link GraphQLSchema#getAdditionalTypes()} returns ALL
     *       non-root types (not just "detached" types as with standard Builder).</li>
     *   <li><b>Validation off by default:</b> Enable with {@link #withValidation(boolean)} if needed.</li>
     * </ul>
     *
     * <h2>Example usage</h2>
     * <pre>{@code
     * GraphQLObjectType queryType = ...;
     * GraphQLObjectType mutationType = ...;
     * Set<GraphQLNamedType> allTypes = ...;  // All types including interface implementations
     * Set<GraphQLDirective> directives = ...;
     *
     * GraphQLSchema schema = new GraphQLSchema.FastBuilder(
     *         GraphQLCodeRegistry.newCodeRegistry(),
     *         queryType,
     *         mutationType,
     *         null)  // no subscription
     *     .addTypes(allTypes)
     *     .additionalDirectives(directives)
     *     .withValidation(true)  // optional, off by default
     *     .build();
     * }</pre>
     *
     * @see GraphQLSchema.Builder for standard schema construction with automatic type discovery
     */
    @ExperimentalApi
    @NullMarked
    public static final class FastBuilder {
        // Fields consumed by the private constructor
        private GraphQLObjectType queryType;
        private @Nullable GraphQLObjectType mutationType;
        private @Nullable GraphQLObjectType subscriptionType;
        private GraphQLObjectType introspectionSchemaType;
        private final Map<String, GraphQLNamedType> typeMap = new LinkedHashMap<>();
        private final Map<String, GraphQLDirective> directiveMap = new LinkedHashMap<>();
        private final List<GraphQLDirective> schemaDirectives = new ArrayList<>();
        private final List<GraphQLAppliedDirective> schemaAppliedDirectives = new ArrayList<>();
        private @Nullable String description;
        private @Nullable SchemaDefinition definition;
        private @Nullable List<SchemaExtensionDefinition> extensionDefinitions;

        // Additional fields for building
        private final GraphQLCodeRegistry.Builder codeRegistryBuilder;
        private final ShallowTypeRefCollector shallowTypeRefCollector = new ShallowTypeRefCollector();
        private boolean validationEnabled = false;

        /**
         * Creates a new FastBuilder with the given code registry builder and root types.
         *
         * @param codeRegistryBuilder the code registry builder (required)
         * @param queryType           the query type (required)
         * @param mutationType        the mutation type (optional, may be null)
         * @param subscriptionType    the subscription type (optional, may be null)
         */
        public FastBuilder(GraphQLCodeRegistry.Builder codeRegistryBuilder,
                           GraphQLObjectType queryType,
                           @Nullable GraphQLObjectType mutationType,
                           @Nullable GraphQLObjectType subscriptionType) {
            this.codeRegistryBuilder = assertNotNull(codeRegistryBuilder, () -> "codeRegistryBuilder can't be null");
            this.queryType = assertNotNull(queryType, () -> "queryType can't be null");
            this.mutationType = mutationType;
            this.subscriptionType = subscriptionType;
            this.introspectionSchemaType = Introspection.__Schema;

            // Add introspection code to the registry
            Introspection.addCodeForIntrospectionTypes(codeRegistryBuilder);

            // Add introspection types to the type map
            // These must be present for introspection queries to work correctly
            addType(Introspection.__Schema);
            addType(Introspection.__Type);
            addType(Introspection.__Field);
            addType(Introspection.__InputValue);
            addType(Introspection.__EnumValue);
            addType(Introspection.__Directive);
            addType(Introspection.__TypeKind);
            addType(Introspection.__DirectiveLocation);

            // Add String and Boolean scalars required by introspection types
            // (e.g., __Type.name returns String, __Field.isDeprecated returns Boolean)
            addType(Scalars.GraphQLString);
            addType(Scalars.GraphQLBoolean);

            // Add root types
            addType(queryType);
            if (mutationType != null) {
                addType(mutationType);
            }
            if (subscriptionType != null) {
                addType(subscriptionType);
            }
        }

        /**
         * Adds a named type to the schema.
         * All non-root types added via this method will be included in {@link GraphQLSchema#getAdditionalTypes()}.
         * <p>
         * <b>Warning:</b> The type object will be mutated during {@link #build()} if it contains
         * {@link GraphQLTypeReference} instances. Do not reuse the same type instance across
         * multiple FastBuilder instances.
         *
         * @param type the named type to add
         *
         * @return this builder for chaining
         */
        public FastBuilder addType(GraphQLNamedType type) {

            String name = type.getName();

            // Enforce uniqueness by name
            GraphQLNamedType existing = typeMap.get(name);
            if (existing != null && existing != type) {
                throw new AssertException(String.format("Type '%s' already exists with a different instance", name));
            }

            // Skip if already added (same instance)
            if (existing != null) {
                return this;
            }

            // Insert into typeMap
            typeMap.put(name, type);

            // Shallow scan via ShallowTypeRefCollector (also tracks interface implementations)
            shallowTypeRefCollector.handleTypeDef(type);

            // For interface types, wire type resolver if present
            if (type instanceof GraphQLInterfaceType) {
                GraphQLInterfaceType interfaceType = (GraphQLInterfaceType) type;
                TypeResolver resolver = interfaceType.getTypeResolver();
                if (resolver != null) {
                    codeRegistryBuilder.typeResolverIfAbsent(interfaceType, resolver);
                }
            }

            // For union types, wire type resolver if present
            if (type instanceof GraphQLUnionType) {
                GraphQLUnionType unionType = (GraphQLUnionType) type;
                TypeResolver resolver = unionType.getTypeResolver();
                if (resolver != null) {
                    codeRegistryBuilder.typeResolverIfAbsent(unionType, resolver);
                }
            }

            return this;
        }

        /**
         * Adds multiple named types to the schema.
         * All non-root types added via this method will be included in {@link GraphQLSchema#getAdditionalTypes()}.
         *
         * @param types the named types to add
         *
         * @return this builder for chaining
         */
        public FastBuilder addTypes(Collection<? extends GraphQLNamedType> types) {
            types.forEach(this::addType);
            return this;
        }

        /**
         * Adds a directive definition to the schema.
         *
         * @param directive the directive to add
         *
         * @return this builder for chaining
         */
        public FastBuilder additionalDirective(GraphQLDirective directive) {
            String name = directive.getName();
            GraphQLDirective existing = directiveMap.get(name);
            if (existing != null && existing != directive) {
                throw new AssertException(String.format("Directive '%s' already exists with a different instance", name));
            }

            if (existing == null) {
                directiveMap.put(name, directive);
                shallowTypeRefCollector.handleDirective(directive);
            }

            return this;
        }

        /**
         * Adds multiple directive definitions to the schema.
         *
         * @param directives the directives to add
         *
         * @return this builder for chaining
         */
        public FastBuilder additionalDirectives(Collection<? extends GraphQLDirective> directives) {
            directives.forEach(this::additionalDirective);
            return this;
        }

        /**
         * Adds a schema-level directive (deprecated, use applied directives).
         *
         * @param directive the directive to add
         *
         * @return this builder for chaining
         */
        public FastBuilder withSchemaDirective(GraphQLDirective directive) {
            schemaDirectives.add(directive);
            return this;
        }

        /**
         * Adds multiple schema-level directives.
         *
         * @param directives the directives to add
         *
         * @return this builder for chaining
         */
        public FastBuilder withSchemaDirectives(Collection<? extends GraphQLDirective> directives) {
            schemaDirectives.addAll(directives);
            return this;
        }

        /**
         * Adds a schema-level applied directive.
         *
         * @param applied the applied directive to add
         *
         * @return this builder for chaining
         */
        public FastBuilder withSchemaAppliedDirective(GraphQLAppliedDirective applied) {
            schemaAppliedDirectives.add(applied);
            // Scan applied directive arguments for type references
            shallowTypeRefCollector.scanAppliedDirectives(singletonList(applied));
            return this;
        }

        /**
         * Adds multiple schema-level applied directives.
         *
         * @param appliedList the applied directives to add
         *
         * @return this builder for chaining
         */
        public FastBuilder withSchemaAppliedDirectives(Collection<? extends GraphQLAppliedDirective> appliedList) {
            for (GraphQLAppliedDirective applied : appliedList) {
                withSchemaAppliedDirective(applied);
            }
            return this;
        }

        /**
         * Sets the schema definition (AST).
         *
         * @param def the schema definition
         *
         * @return this builder for chaining
         */
        public FastBuilder definition(SchemaDefinition def) {
            this.definition = def;
            return this;
        }

        /**
         * Sets the schema extension definitions (AST).
         *
         * @param defs the extension definitions
         *
         * @return this builder for chaining
         */
        public FastBuilder extensionDefinitions(List<SchemaExtensionDefinition> defs) {
            this.extensionDefinitions = defs;
            return this;
        }

        /**
         * Sets the schema description.
         *
         * @param description the description
         *
         * @return this builder for chaining
         */
        public FastBuilder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the introspection schema type.
         *
         * @param type the introspection schema type
         *
         * @return this builder for chaining
         */
        public FastBuilder introspectionSchemaType(GraphQLObjectType type) {
            this.introspectionSchemaType = type;
            return this;
        }

        /**
         * Enables or disables schema validation.
         *
         * @param enabled true to enable validation, false to disable
         *
         * @return this builder for chaining
         */
        public FastBuilder withValidation(boolean enabled) {
            this.validationEnabled = enabled;
            return this;
        }

        /**
         * Builds the GraphQL schema.
         * <p>
         * <b>Warning:</b> This method mutates the type and directive objects that were added to this
         * builder. Any {@link GraphQLTypeReference} instances within those objects are replaced
         * in-place with the actual resolved types. After calling this method, the added types
         * should not be reused with another FastBuilder.
         *
         * @return the built schema
         *
         * @throws InvalidSchemaException if validation is enabled and the schema is invalid
         * @throws AssertException        if a type reference cannot be resolved or if an interface/union
         *                                type is missing a type resolver
         */
        public GraphQLSchema build() {
            // Validate type resolvers for all interfaces and unions
            for (GraphQLNamedType type : typeMap.values()) {
                if (type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType) {
                    String typeName = type.getName();
                    if (!codeRegistryBuilder.hasTypeResolver(typeName)) {
                        String typeKind = type instanceof GraphQLInterfaceType ? "interface" : "union";
                        assertShouldNeverHappen("You MUST provide a type resolver for the %s type '%s'", typeKind, typeName);
                    }
                }
            }

            // Replace type references
            shallowTypeRefCollector.replaceTypes(typeMap);

            // Add built-in directives if missing
            Directives.BUILT_IN_DIRECTIVES.forEach(this::addDirectiveIfMissing);

            // Create schema via private constructor
            GraphQLSchema schema = new GraphQLSchema(this);

            // Optional GraphQL spec validation
            if (validationEnabled) {
                Collection<SchemaValidationError> errors = new SchemaValidator().validateSchema(schema);
                if (!errors.isEmpty()) {
                    throw new InvalidSchemaException(errors);
                }
            }

            return schema;
        }

        private void addDirectiveIfMissing(GraphQLDirective directive) {
            if (!directiveMap.containsKey(directive.getName())) {
                directiveMap.put(directive.getName(), directive);
            }
        }
    }
}
