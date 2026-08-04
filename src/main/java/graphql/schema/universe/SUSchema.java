package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLSchema;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * An immutable graph view selecting one registered schema from a {@link SchemaUniverse}.
 *
 * <p>This schema is supported only while its exact instance remains registered in its universe.
 * Removing it ends that supported lifetime even if application code retains a reference. Methods
 * are not guaranteed to remain usable after removal, and its vertices may later be reclaimed.</p>
 */
@ExperimentalApi
@NullMarked
public final class SUSchema {

    private final SchemaUniverse universe;
    private final SUSchemaRoot root;
    private final PersistentIntMap<SUNamedType> namedTypesByNameId;
    private final PersistentEdgeMap edgeMap;
    private final PersistentIntMap<Map<String, Object>> vertexMetadata;
    private final int namedTypeCount;
    private final int storedEdgeCount;

    @Internal
    public SUSchema(
            SchemaUniverse universe,
            SUSchemaRoot root,
            PersistentIntMap<SUNamedType> namedTypesByNameId,
            PersistentEdgeMap edgeMap,
            PersistentIntMap<Map<String, Object>> vertexMetadata,
            int namedTypeCount,
            int storedEdgeCount) {
        this.universe = assertNotNull(universe);
        this.root = assertNotNull(root);
        this.namedTypesByNameId = assertNotNull(namedTypesByNameId);
        this.edgeMap = assertNotNull(edgeMap);
        this.vertexMetadata = assertNotNull(vertexMetadata);
        this.namedTypeCount = namedTypeCount;
        this.storedEdgeCount = storedEdgeCount;
    }

    public String getName() {
        return assertNotNull(root.getName());
    }

    public SUSchemaRoot getRoot() {
        return root;
    }

    /**
     * Returns this snapshot's user-controlled metadata for a vertex.
     *
     * <p>The returned map is immutable, although values supplied by the application may themselves
     * be mutable. Metadata has no GraphQL semantics and is not exported.</p>
     *
     * @param vertex the vertex
     *
     * @return the metadata, or an empty map when none is stored
     */
    public Map<String, Object> getVertexMetadata(SUVertex vertex) {
        assertOwned(vertex);
        Map<String, Object> metadata = vertexMetadata.get(vertex.getId());
        return metadata == null ? Collections.emptyMap() : metadata;
    }

    /**
     * Returns one user-controlled metadata value for a vertex.
     *
     * @param vertex the vertex
     * @param key the metadata key
     *
     * @return the value, or {@code null} when absent
     */
    public @Nullable Object getVertexMetadata(SUVertex vertex, String key) {
        return getVertexMetadata(vertex).get(assertNotNull(key));
    }

    /**
     * Exports this snapshot as an unexecutable {@link GraphQLSchema}.
     *
     * <p>The result preserves the GraphQL type-system topology and metadata represented by this
     * schema. User-controlled vertex metadata and runtime wiring are not exported.</p>
     */
    public GraphQLSchema toGraphQLSchema() {
        return new SUExporter(this).exportSchema();
    }

    public SUObjectType getQueryType() {
        return (SUObjectType) assertNotNull(getSingleTarget(root, SUEdgeKind.QUERY_TYPE));
    }

    public @Nullable SUObjectType getMutationType() {
        return (SUObjectType) getSingleTarget(root, SUEdgeKind.MUTATION_TYPE);
    }

    public @Nullable SUObjectType getSubscriptionType() {
        return (SUObjectType) getSingleTarget(root, SUEdgeKind.SUBSCRIPTION_TYPE);
    }

    public List<SUObjectType> getRootTypes() {
        List<SUObjectType> result = new ArrayList<>(3);
        result.add(getQueryType());
        SUObjectType mutationType = getMutationType();
        if (mutationType != null) {
            result.add(mutationType);
        }
        SUObjectType subscriptionType = getSubscriptionType();
        if (subscriptionType != null) {
            result.add(subscriptionType);
        }
        return Collections.unmodifiableList(result);
    }

    public List<SUNamedType> getTypes() {
        List<SUNamedType> result = new ArrayList<>(namedTypeCount);
        namedTypesByNameId.forEachEntry((nameId, type) -> result.add(type));
        result.sort(Comparator.comparing(type -> assertNotNull(type.getName())));
        return Collections.unmodifiableList(result);
    }

    public @Nullable SUNamedType getType(String name) {
        int nameId = universe.getNameId(assertNotNull(name));
        return nameId < 0 ? null : namedTypesByNameId.get(nameId);
    }

    public @Nullable SUObjectType getObjectType(String name) {
        return getTypeAs(name, SUObjectType.class);
    }

    public @Nullable SUInterfaceType getInterfaceType(String name) {
        return getTypeAs(name, SUInterfaceType.class);
    }

    public @Nullable SUUnionType getUnionType(String name) {
        return getTypeAs(name, SUUnionType.class);
    }

    public @Nullable SUEnumType getEnumType(String name) {
        return getTypeAs(name, SUEnumType.class);
    }

    public @Nullable SUScalarType getScalarType(String name) {
        return getTypeAs(name, SUScalarType.class);
    }

    public @Nullable SUInputObjectType getInputObjectType(String name) {
        return getTypeAs(name, SUInputObjectType.class);
    }

    public List<SUDirective> getDirectiveDefinitions() {
        return getTypedChildren(root, SUEdgeKind.DIRECTIVE_DEFINITION, SUDirective.class);
    }

    public @Nullable SUDirective getDirectiveDefinition(String name) {
        return getTypedChild(root, SUEdgeKind.DIRECTIVE_DEFINITION, name, SUDirective.class);
    }

    public List<SUField> getFields(SUObjectType objectType) {
        return getTypedChildren(objectType, SUEdgeKind.FIELD, SUField.class);
    }

    public List<SUField> getFields(SUInterfaceType interfaceType) {
        return getTypedChildren(interfaceType, SUEdgeKind.FIELD, SUField.class);
    }

    public @Nullable SUField getField(SUObjectType objectType, String name) {
        return getTypedChild(objectType, SUEdgeKind.FIELD, name, SUField.class);
    }

    public @Nullable SUField getField(SUInterfaceType interfaceType, String name) {
        return getTypedChild(interfaceType, SUEdgeKind.FIELD, name, SUField.class);
    }

    public List<SUArgument> getArguments(SUField field) {
        return getTypedChildren(field, SUEdgeKind.ARGUMENT, SUArgument.class);
    }

    public List<SUArgument> getArguments(SUDirective directive) {
        return getTypedChildren(directive, SUEdgeKind.ARGUMENT, SUArgument.class);
    }

    public List<SUAppliedDirectiveArgument> getArguments(SUAppliedDirective directive) {
        assertOwned(directive);
        return directive.getArguments();
    }

    public @Nullable SUArgument getArgument(SUField field, String name) {
        return getTypedChild(field, SUEdgeKind.ARGUMENT, name, SUArgument.class);
    }

    public @Nullable SUArgument getArgument(SUDirective directive, String name) {
        return getTypedChild(directive, SUEdgeKind.ARGUMENT, name, SUArgument.class);
    }

    public @Nullable SUAppliedDirectiveArgument getArgument(
            SUAppliedDirective directive,
            String name) {
        assertOwned(directive);
        return directive.getArgument(name);
    }

    public @Nullable SUType getType(SUField field) {
        return (SUType) getSingleTarget(field, SUEdgeKind.TYPE);
    }

    public @Nullable SUType getType(SUArgument argument) {
        return (SUType) getSingleTarget(argument, SUEdgeKind.TYPE);
    }

    public @Nullable SUType getType(SUInputField inputField) {
        return (SUType) getSingleTarget(inputField, SUEdgeKind.TYPE);
    }

    public SUType getType(SUAppliedDirectiveArgument argument) {
        assertOwned(argument);
        return (SUType) universe.getVertex(argument.getTypeId());
    }

    public List<SUInterfaceType> getInterfaces(SUObjectType objectType) {
        return getTypedChildren(objectType, SUEdgeKind.IMPLEMENTS, SUInterfaceType.class);
    }

    public List<SUInterfaceType> getInterfaces(SUInterfaceType interfaceType) {
        return getTypedChildren(interfaceType, SUEdgeKind.IMPLEMENTS, SUInterfaceType.class);
    }

    public @Nullable SUInterfaceType getInterface(SUObjectType objectType, String name) {
        return getTypedChild(objectType, SUEdgeKind.IMPLEMENTS, name, SUInterfaceType.class);
    }

    public @Nullable SUInterfaceType getInterface(SUInterfaceType interfaceType, String name) {
        return getTypedChild(interfaceType, SUEdgeKind.IMPLEMENTS, name, SUInterfaceType.class);
    }

    public List<SUObjectType> getUnionMembers(SUUnionType unionType) {
        return getTypedChildren(unionType, SUEdgeKind.UNION_MEMBER, SUObjectType.class);
    }

    public @Nullable SUObjectType getUnionMember(SUUnionType unionType, String name) {
        return getTypedChild(unionType, SUEdgeKind.UNION_MEMBER, name, SUObjectType.class);
    }

    public List<SUEnumValue> getEnumValues(SUEnumType enumType) {
        return getTypedChildren(enumType, SUEdgeKind.ENUM_VALUE, SUEnumValue.class);
    }

    public @Nullable SUEnumValue getEnumValue(SUEnumType enumType, String name) {
        return getTypedChild(enumType, SUEdgeKind.ENUM_VALUE, name, SUEnumValue.class);
    }

    public List<SUInputField> getInputFields(SUInputObjectType inputObjectType) {
        return getTypedChildren(inputObjectType, SUEdgeKind.INPUT_FIELD, SUInputField.class);
    }

    public @Nullable SUInputField getInputField(SUInputObjectType inputObjectType, String name) {
        return getTypedChild(inputObjectType, SUEdgeKind.INPUT_FIELD, name, SUInputField.class);
    }

    public List<SUAppliedDirective> getSchemaAppliedDirectives() {
        return getAppliedDirectives(root);
    }

    public List<SUAppliedDirective> getSchemaAppliedDirectives(String name) {
        return getAppliedDirectives(root, name);
    }

    public List<SUAppliedDirective> getAppliedDirectives(SUVertex container) {
        return getTypedChildren(
                container,
                SUEdgeKind.APPLIED_DIRECTIVE,
                SUAppliedDirective.class);
    }

    public List<SUAppliedDirective> getAppliedDirectives(SUVertex container, String name) {
        assertNotNull(name);
        List<SUAppliedDirective> directives = getAppliedDirectives(container);
        if (directives.isEmpty()) {
            return directives;
        }
        List<SUAppliedDirective> result = new ArrayList<>();
        for (SUAppliedDirective directive : directives) {
            if (name.equals(directive.getName())) {
                result.add(directive);
            }
        }
        return result.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(result);
    }

    public @Nullable SUType getWrappedType(SUListType listType) {
        return (SUType) getSingleTarget(listType, SUEdgeKind.WRAPPED_TYPE);
    }

    public @Nullable SUType getWrappedType(SUNonNullType nonNullType) {
        return (SUType) getSingleTarget(nonNullType, SUEdgeKind.WRAPPED_TYPE);
    }

    /**
     * Returns the number of adjacency bindings retained by this snapshot.
     *
     * <p>This can include bindings for vertices that are not currently reachable from the schema
     * root. Such dormant bindings allow a derived schema to reattach a subgraph cheaply.</p>
     */
    public int getStoredEdgeCount() {
        return storedEdgeCount;
    }

    @Internal
    public List<SUVertex> getChildren(
            SUVertex source,
            SUEdgeKind kind) {
        assertOwned(source);
        PackedEdgeSet edges = edgeMap.get(source.getId());
        int start = edges.firstIndex(kind);
        int end = edges.endIndex(kind);
        if (start == end) {
            return Collections.emptyList();
        }
        List<SUVertex> result = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            result.add(universe.getVertex(edges.targetIdAt(i)));
        }
        return Collections.unmodifiableList(result);
    }

    @Internal
    public @Nullable SUVertex getChild(
            SUVertex source,
            SUEdgeKind kind,
            String name) {
        assertOwned(source);
        int nameId = universe.getNameId(assertNotNull(name));
        if (nameId < 0) {
            return null;
        }
        int targetId = edgeMap.get(source.getId()).targetByName(kind, nameId);
        return targetId < 0 ? null : universe.getVertex(targetId);
    }

    @Internal
    public boolean containsEdge(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        assertOwned(source);
        assertOwned(target);
        return edgeMap.get(source.getId()).contains(kind, target.getNameId(), target.getId());
    }

    public SUSchema transform(
            String name,
            Consumer<SUSchemaBuilder> builderConsumer) {
        SUSchemaBuilder builder = universe.transformBuilder(this, name);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Internal
    public SchemaUniverse getUniverse() {
        return universe;
    }

    @Internal
    public PersistentEdgeMap getEdgeMap() {
        return edgeMap;
    }

    @Internal
    public PersistentIntMap<SUNamedType> getNamedTypesByNameId() {
        return namedTypesByNameId;
    }

    @Internal
    public int getNamedTypeCount() {
        return namedTypeCount;
    }

    @Internal
    public PersistentIntMap<Map<String, Object>> getVertexMetadataMap() {
        return vertexMetadata;
    }

    @Internal
    public PackedEdgeSet getPackedEdges(SUVertex source) {
        assertOwned(source);
        return edgeMap.get(source.getId());
    }

    @Internal
    public boolean sharesOutgoingEdgesWith(
            SUSchema other,
            SUVertex source) {
        assertTrue(universe == other.universe, "Schemas belong to different schema universes");
        return getPackedEdges(source) == other.getPackedEdges(source);
    }

    private @Nullable SUVertex getSingleTarget(
            SUVertex source,
            SUEdgeKind kind) {
        assertOwned(source);
        int targetId = edgeMap.get(source.getId()).firstTarget(kind);
        return targetId < 0 ? null : universe.getVertex(targetId);
    }

    private <T extends SUVertex> List<T> getTypedChildren(
            SUVertex source,
            SUEdgeKind kind,
            Class<T> targetType) {
        List<SUVertex> children = getChildren(source, kind);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(children.size());
        for (SUVertex child : children) {
            result.add(targetType.cast(child));
        }
        return Collections.unmodifiableList(result);
    }

    private <T extends SUVertex> @Nullable T getTypedChild(
            SUVertex source,
            SUEdgeKind kind,
            String name,
            Class<T> targetType) {
        SUVertex child = getChild(source, kind, name);
        return child == null ? null : targetType.cast(child);
    }

    private <T extends SUVertex> @Nullable T getTypeAs(String name, Class<T> type) {
        SUNamedType vertex = getType(name);
        return type.isInstance(vertex) ? type.cast(vertex) : null;
    }

    private void assertOwned(SUVertex vertex) {
        assertTrue(universe.owns(vertex), "Vertex %s belongs to another schema universe", vertex);
    }

    private void assertOwned(SUAppliedDirectiveArgument argument) {
        assertTrue(
                argument.getUniverse() == universe,
                "Applied directive argument %s belongs to another schema universe",
                argument);
    }
}
