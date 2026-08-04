package graphql.schema.universe;

import com.google.common.collect.ImmutableMap;
import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * Builds an immutable schema snapshot, optionally by changing an existing snapshot.
 */
@ExperimentalApi
@NullMarked
public final class SUSchemaBuilder {

    private final SchemaUniverse universe;
    private final SUSchemaRoot root;
    private final @Nullable SUSchema baseSchema;
    private final Map<Integer, MutablePackedEdgeSet> changedEdges = new LinkedHashMap<>();
    private final Map<Integer, LinkedHashMap<String, Object>> changedVertexMetadata =
            new LinkedHashMap<>();
    private PersistentIntMap<SUNamedType> namedTypesByNameId;
    private int namedTypeCount;
    private boolean built;

    @Internal
    public SUSchemaBuilder(
            SchemaUniverse universe,
            SUSchemaRoot root,
            @Nullable SUSchema baseSchema) {
        this.universe = assertNotNull(universe);
        this.root = assertNotNull(root);
        this.baseSchema = baseSchema;
        this.namedTypesByNameId = baseSchema == null
                ? PersistentIntMap.empty()
                : baseSchema.getNamedTypesByNameId();
        this.namedTypeCount = baseSchema == null ? 0 : baseSchema.getNamedTypeCount();
        copyRootEdges(baseSchema);
        copyRootMetadata(baseSchema);
    }

    public SUSchemaBuilder queryType(SUObjectType queryType) {
        return setQueryType(queryType);
    }

    public SUSchemaBuilder setQueryType(SUObjectType queryType) {
        return setEdge(root, SUEdgeKind.QUERY_TYPE, queryType);
    }

    public SUSchemaRoot getRoot() {
        return root;
    }

    /**
     * Replaces this snapshot's user-controlled metadata for a vertex.
     *
     * <p>The map is copied immediately. An empty map removes the metadata binding.</p>
     *
     * @param vertex the vertex
     * @param metadata the metadata
     *
     * @return this builder
     */
    public SUSchemaBuilder vertexMetadata(
            SUVertex vertex,
            Map<String, Object> metadata) {
        assertCanChange();
        assertOwned(vertex);
        changedVertexMetadata.put(
                vertex.getId(),
                copyMetadata(metadata));
        return this;
    }

    /**
     * Adds or replaces one user-controlled metadata value.
     *
     * @param vertex the vertex
     * @param key the metadata key
     * @param value the metadata value
     *
     * @return this builder
     */
    public SUSchemaBuilder vertexMetadata(
            SUVertex vertex,
            String key,
            Object value) {
        assertCanChange();
        assertOwned(vertex);
        mutableVertexMetadata(vertex).put(
                assertNotNull(key),
                assertNotNull(value));
        return this;
    }

    /**
     * Removes one user-controlled metadata value.
     *
     * @param vertex the vertex
     * @param key the metadata key
     *
     * @return this builder
     */
    public SUSchemaBuilder removeVertexMetadata(
            SUVertex vertex,
            String key) {
        assertCanChange();
        assertOwned(vertex);
        mutableVertexMetadata(vertex).remove(assertNotNull(key));
        return this;
    }

    /**
     * Removes all user-controlled metadata for a vertex.
     *
     * @param vertex the vertex
     *
     * @return this builder
     */
    public SUSchemaBuilder clearVertexMetadata(SUVertex vertex) {
        assertCanChange();
        assertOwned(vertex);
        changedVertexMetadata.put(vertex.getId(), new LinkedHashMap<>());
        return this;
    }

    /**
     * Copies user-controlled metadata between vertices in this snapshot.
     *
     * @param source the source vertex
     * @param target the target vertex
     *
     * @return this builder
     */
    public SUSchemaBuilder copyVertexMetadata(
            SUVertex source,
            SUVertex target) {
        assertCanChange();
        assertOwned(source);
        assertOwned(target);
        changedVertexMetadata.put(
                target.getId(),
                new LinkedHashMap<>(currentVertexMetadata(source)));
        return this;
    }

    public SUSchemaBuilder mutationType(@Nullable SUObjectType mutationType) {
        return setMutationType(mutationType);
    }

    public SUSchemaBuilder setMutationType(@Nullable SUObjectType mutationType) {
        return setOptionalEdge(SUEdgeKind.MUTATION_TYPE, mutationType);
    }

    public SUSchemaBuilder subscriptionType(@Nullable SUObjectType subscriptionType) {
        return setSubscriptionType(subscriptionType);
    }

    public SUSchemaBuilder setSubscriptionType(@Nullable SUObjectType subscriptionType) {
        return setOptionalEdge(SUEdgeKind.SUBSCRIPTION_TYPE, subscriptionType);
    }

    public SUSchemaBuilder type(SUNamedType type) {
        return addType(type);
    }

    /**
     * Adds a named type to this schema.
     *
     * @param type the named type
     *
     * @return this builder
     */
    public SUSchemaBuilder addType(SUNamedType type) {
        assertCanChange();
        assertOwned(type);
        int nameId = type.getNameId();
        SUNamedType existing = namedTypesByNameId.get(nameId);
        assertTrue(
                existing == null || existing == type,
                "Schema already contains a different type named '%s'",
                assertNotNull(type.getName()));
        if (existing == null) {
            namedTypesByNameId = namedTypesByNameId.put(nameId, type);
            namedTypeCount++;
        }
        return this;
    }

    /**
     * Removes a named type from this schema.
     *
     * @param type the named type
     *
     * @return this builder
     */
    public SUSchemaBuilder removeType(SUNamedType type) {
        assertCanChange();
        assertOwned(type);
        int nameId = type.getNameId();
        if (namedTypesByNameId.get(nameId) == type) {
            namedTypesByNameId = namedTypesByNameId.remove(nameId);
            namedTypeCount--;
        }
        return this;
    }

    /**
     * Removes the named type with the given name from this schema.
     *
     * @param name the type name
     *
     * @return this builder
     */
    public SUSchemaBuilder removeType(String name) {
        assertCanChange();
        int nameId = universe.getNameId(assertNotNull(name));
        if (nameId >= 0 && namedTypesByNameId.get(nameId) != null) {
            namedTypesByNameId = namedTypesByNameId.remove(nameId);
            namedTypeCount--;
        }
        return this;
    }

    /**
     * Removes every named type except the current operation types.
     *
     * @return this builder
     */
    public SUSchemaBuilder clearTypes() {
        assertCanChange();
        PackedEdgeSet rootEdges = mutableEdges(root).freeze();
        namedTypesByNameId = PersistentIntMap.empty();
        namedTypeCount = 0;
        retainOperationType(rootEdges, SUEdgeKind.QUERY_TYPE);
        retainOperationType(rootEdges, SUEdgeKind.MUTATION_TYPE);
        retainOperationType(rootEdges, SUEdgeKind.SUBSCRIPTION_TYPE);
        return this;
    }

    public SUSchemaBuilder directiveDefinition(SUDirective directive) {
        return addDirectiveDefinition(directive);
    }

    public SUSchemaBuilder addDirectiveDefinition(SUDirective directive) {
        return addEdge(root, SUEdgeKind.DIRECTIVE_DEFINITION, directive);
    }

    public SUSchemaBuilder removeDirectiveDefinition(SUDirective directive) {
        return removeEdge(root, SUEdgeKind.DIRECTIVE_DEFINITION, directive);
    }

    public SUSchemaBuilder removeDirectiveDefinition(String name) {
        return removeEdge(root, SUEdgeKind.DIRECTIVE_DEFINITION, name);
    }

    public SUSchemaBuilder clearDirectiveDefinitions() {
        return removeEdges(root, SUEdgeKind.DIRECTIVE_DEFINITION);
    }

    public SUSchemaBuilder addSchemaAppliedDirective(SUAppliedDirective directive) {
        return addAppliedDirective(root, directive);
    }

    public SUSchemaBuilder removeSchemaAppliedDirective(SUAppliedDirective directive) {
        return removeAppliedDirective(root, directive);
    }

    public SUSchemaBuilder removeSchemaAppliedDirectives(String name) {
        return removeAppliedDirectives(root, name);
    }

    public SUSchemaBuilder clearSchemaAppliedDirectives() {
        return clearAppliedDirectives(root);
    }

    public SUSchemaBuilder replaceSchemaAppliedDirective(
            SUAppliedDirective current,
            SUAppliedDirective replacement) {
        return replaceAppliedDirective(root, current, replacement);
    }

    public SUSchemaBuilder replaceSchemaAppliedDirectives(
            List<SUAppliedDirective> directives) {
        return replaceAppliedDirectives(root, directives);
    }

    public SUSchemaBuilder addField(SUObjectType objectType, SUField field) {
        return addEdge(objectType, SUEdgeKind.FIELD, field);
    }

    public SUSchemaBuilder addField(SUInterfaceType interfaceType, SUField field) {
        return addEdge(interfaceType, SUEdgeKind.FIELD, field);
    }

    public SUSchemaBuilder removeField(SUObjectType objectType, SUField field) {
        return removeEdge(objectType, SUEdgeKind.FIELD, field);
    }

    public SUSchemaBuilder removeField(SUInterfaceType interfaceType, SUField field) {
        return removeEdge(interfaceType, SUEdgeKind.FIELD, field);
    }

    public SUSchemaBuilder removeField(SUObjectType objectType, String name) {
        return removeEdge(objectType, SUEdgeKind.FIELD, name);
    }

    public SUSchemaBuilder removeField(SUInterfaceType interfaceType, String name) {
        return removeEdge(interfaceType, SUEdgeKind.FIELD, name);
    }

    public SUSchemaBuilder clearFields(SUObjectType objectType) {
        return removeEdges(objectType, SUEdgeKind.FIELD);
    }

    public SUSchemaBuilder clearFields(SUInterfaceType interfaceType) {
        return removeEdges(interfaceType, SUEdgeKind.FIELD);
    }

    public SUSchemaBuilder setFieldType(SUField field, SUType type) {
        assertOutputType(type);
        return setEdge(field, SUEdgeKind.TYPE, type);
    }

    public SUSchemaBuilder addArgument(SUField field, SUArgument argument) {
        return addEdge(field, SUEdgeKind.ARGUMENT, argument);
    }

    public SUSchemaBuilder addArgument(SUDirective directive, SUArgument argument) {
        return addEdge(directive, SUEdgeKind.ARGUMENT, argument);
    }

    public SUSchemaBuilder removeArgument(SUField field, SUArgument argument) {
        return removeEdge(field, SUEdgeKind.ARGUMENT, argument);
    }

    public SUSchemaBuilder removeArgument(SUDirective directive, SUArgument argument) {
        return removeEdge(directive, SUEdgeKind.ARGUMENT, argument);
    }

    public SUSchemaBuilder removeArgument(SUField field, String name) {
        return removeEdge(field, SUEdgeKind.ARGUMENT, name);
    }

    public SUSchemaBuilder removeArgument(SUDirective directive, String name) {
        return removeEdge(directive, SUEdgeKind.ARGUMENT, name);
    }

    public SUSchemaBuilder clearArguments(SUField field) {
        return removeEdges(field, SUEdgeKind.ARGUMENT);
    }

    public SUSchemaBuilder clearArguments(SUDirective directive) {
        return removeEdges(directive, SUEdgeKind.ARGUMENT);
    }

    public SUSchemaBuilder setArgumentType(SUArgument argument, SUType type) {
        assertInputType(type);
        return setEdge(argument, SUEdgeKind.TYPE, type);
    }

    public SUSchemaBuilder addInterface(
            SUObjectType objectType,
            SUInterfaceType interfaceType) {
        return addEdge(objectType, SUEdgeKind.IMPLEMENTS, interfaceType);
    }

    public SUSchemaBuilder addInterface(
            SUInterfaceType implementingType,
            SUInterfaceType interfaceType) {
        return addEdge(implementingType, SUEdgeKind.IMPLEMENTS, interfaceType);
    }

    public SUSchemaBuilder removeInterface(
            SUObjectType objectType,
            SUInterfaceType interfaceType) {
        return removeEdge(objectType, SUEdgeKind.IMPLEMENTS, interfaceType);
    }

    public SUSchemaBuilder removeInterface(
            SUInterfaceType implementingType,
            SUInterfaceType interfaceType) {
        return removeEdge(implementingType, SUEdgeKind.IMPLEMENTS, interfaceType);
    }

    public SUSchemaBuilder removeInterface(SUObjectType objectType, String name) {
        return removeEdge(objectType, SUEdgeKind.IMPLEMENTS, name);
    }

    public SUSchemaBuilder removeInterface(SUInterfaceType interfaceType, String name) {
        return removeEdge(interfaceType, SUEdgeKind.IMPLEMENTS, name);
    }

    public SUSchemaBuilder clearInterfaces(SUObjectType objectType) {
        return removeEdges(objectType, SUEdgeKind.IMPLEMENTS);
    }

    public SUSchemaBuilder clearInterfaces(SUInterfaceType interfaceType) {
        return removeEdges(interfaceType, SUEdgeKind.IMPLEMENTS);
    }

    public SUSchemaBuilder addUnionMember(SUUnionType unionType, SUObjectType member) {
        return addEdge(unionType, SUEdgeKind.UNION_MEMBER, member);
    }

    public SUSchemaBuilder removeUnionMember(SUUnionType unionType, SUObjectType member) {
        return removeEdge(unionType, SUEdgeKind.UNION_MEMBER, member);
    }

    public SUSchemaBuilder removeUnionMember(SUUnionType unionType, String name) {
        return removeEdge(unionType, SUEdgeKind.UNION_MEMBER, name);
    }

    public SUSchemaBuilder clearUnionMembers(SUUnionType unionType) {
        return removeEdges(unionType, SUEdgeKind.UNION_MEMBER);
    }

    public SUSchemaBuilder addEnumValue(SUEnumType enumType, SUEnumValue value) {
        return addEdge(enumType, SUEdgeKind.ENUM_VALUE, value);
    }

    public SUSchemaBuilder removeEnumValue(SUEnumType enumType, SUEnumValue value) {
        return removeEdge(enumType, SUEdgeKind.ENUM_VALUE, value);
    }

    public SUSchemaBuilder removeEnumValue(SUEnumType enumType, String name) {
        return removeEdge(enumType, SUEdgeKind.ENUM_VALUE, name);
    }

    public SUSchemaBuilder clearEnumValues(SUEnumType enumType) {
        return removeEdges(enumType, SUEdgeKind.ENUM_VALUE);
    }

    public SUSchemaBuilder addInputField(
            SUInputObjectType inputObjectType,
            SUInputField field) {
        return addEdge(inputObjectType, SUEdgeKind.INPUT_FIELD, field);
    }

    public SUSchemaBuilder removeInputField(
            SUInputObjectType inputObjectType,
            SUInputField field) {
        return removeEdge(inputObjectType, SUEdgeKind.INPUT_FIELD, field);
    }

    public SUSchemaBuilder removeInputField(SUInputObjectType inputObjectType, String name) {
        return removeEdge(inputObjectType, SUEdgeKind.INPUT_FIELD, name);
    }

    public SUSchemaBuilder clearInputFields(SUInputObjectType inputObjectType) {
        return removeEdges(inputObjectType, SUEdgeKind.INPUT_FIELD);
    }

    public SUSchemaBuilder setInputFieldType(SUInputField field, SUType type) {
        assertInputType(type);
        return setEdge(field, SUEdgeKind.TYPE, type);
    }

    public SUSchemaBuilder addAppliedDirective(
            SUAppliedDirectiveContainer container,
            SUAppliedDirective directive) {
        return addEdge(containerVertex(container), SUEdgeKind.APPLIED_DIRECTIVE, directive);
    }

    public SUSchemaBuilder removeAppliedDirective(
            SUAppliedDirectiveContainer container,
            SUAppliedDirective directive) {
        return removeEdge(containerVertex(container), SUEdgeKind.APPLIED_DIRECTIVE, directive);
    }

    public SUSchemaBuilder removeAppliedDirectives(
            SUAppliedDirectiveContainer container,
            String name) {
        return removeEdge(containerVertex(container), SUEdgeKind.APPLIED_DIRECTIVE, name);
    }

    public SUSchemaBuilder clearAppliedDirectives(SUAppliedDirectiveContainer container) {
        return removeEdges(containerVertex(container), SUEdgeKind.APPLIED_DIRECTIVE);
    }

    public SUSchemaBuilder replaceAppliedDirective(
            SUAppliedDirectiveContainer container,
            SUAppliedDirective current,
            SUAppliedDirective replacement) {
        assertCanChange();
        SUVertex source = containerVertex(container);
        assertOwned(current);
        assertEdgeShape(source, SUEdgeKind.APPLIED_DIRECTIVE, replacement);
        long currentEdge = PackedEdgeSet.pack(
                SUEdgeKind.APPLIED_DIRECTIVE,
                current.getNameId(),
                current.getId());
        long replacementEdge = PackedEdgeSet.pack(
                SUEdgeKind.APPLIED_DIRECTIVE,
                replacement.getNameId(),
                replacement.getId());
        assertTrue(
                mutableEdges(source).replace(currentEdge, replacementEdge),
                "Applied directive %s is not attached to container %s",
                current,
                source);
        return this;
    }

    public SUSchemaBuilder replaceAppliedDirectives(
            SUAppliedDirectiveContainer container,
            List<SUAppliedDirective> directives) {
        assertCanChange();
        SUVertex source = containerVertex(container);
        List<SUAppliedDirective> replacements = assertNotNull(directives);
        for (SUAppliedDirective directive : replacements) {
            assertEdgeShape(source, SUEdgeKind.APPLIED_DIRECTIVE, directive);
        }
        MutablePackedEdgeSet edges = mutableEdges(source);
        edges.removeKind(SUEdgeKind.APPLIED_DIRECTIVE);
        for (SUAppliedDirective directive : replacements) {
            edges.add(PackedEdgeSet.pack(
                    SUEdgeKind.APPLIED_DIRECTIVE,
                    directive.getNameId(),
                    directive.getId()));
        }
        return this;
    }

    public SUSchemaBuilder setWrappedType(SUListType listType, SUType wrappedType) {
        return setEdge(listType, SUEdgeKind.WRAPPED_TYPE, wrappedType);
    }

    public SUSchemaBuilder setWrappedType(SUNonNullType nonNullType, SUType wrappedType) {
        return setEdge(nonNullType, SUEdgeKind.WRAPPED_TYPE, wrappedType);
    }

    private SUSchemaBuilder addEdge(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        assertCanChange();
        assertEdgeShape(source, kind, target);
        addNamedEndpoint(source);
        addNamedEndpoint(target);
        mutableEdges(source).add(PackedEdgeSet.pack(kind, target.getNameId(), target.getId()));
        return this;
    }

    private SUSchemaBuilder setEdge(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        assertCanChange();
        assertTrue(kind.isSingle(), "Edge kind %s is not single-valued", kind);
        assertEdgeShape(source, kind, target);
        addNamedEndpoint(source);
        addNamedEndpoint(target);
        MutablePackedEdgeSet edges = mutableEdges(source);
        edges.removeKind(kind);
        edges.add(PackedEdgeSet.pack(kind, target.getNameId(), target.getId()));
        return this;
    }

    private void addNamedEndpoint(SUVertex vertex) {
        if (vertex instanceof SUNamedType) {
            addType((SUNamedType) vertex);
        }
    }

    private SUSchemaBuilder removeEdge(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        assertCanChange();
        assertOwned(source);
        assertOwned(target);
        mutableEdges(source).remove(PackedEdgeSet.pack(kind, target.getNameId(), target.getId()));
        return this;
    }

    private SUSchemaBuilder removeEdge(
            SUVertex source,
            SUEdgeKind kind,
            String targetName) {
        assertCanChange();
        assertOwned(source);
        int nameId = universe.getNameId(targetName);
        if (nameId >= 0) {
            mutableEdges(source).removeByName(kind, nameId);
        }
        return this;
    }

    private SUSchemaBuilder removeEdges(
            SUVertex source,
            SUEdgeKind kind) {
        assertCanChange();
        assertOwned(source);
        mutableEdges(source).removeKind(kind);
        return this;
    }

    public SUSchema build() {
        assertCanChange();
        built = true;
        PersistentEdgeMap edgeMap = baseEdgeMap();
        PersistentIntMap<Map<String, Object>> metadataMap =
                baseVertexMetadataMap();
        int edgeCount = baseEdgeCount();
        for (Map.Entry<Integer, MutablePackedEdgeSet> entry : changedEdges.entrySet()) {
            int sourceId = entry.getKey();
            PackedEdgeSet oldEdges = edgeMap.get(sourceId);
            PackedEdgeSet newEdges = entry.getValue().freeze();
            edgeCount += newEdges.size() - oldEdges.size();
            edgeMap = edgeMap.put(sourceId, newEdges);
        }
        for (Map.Entry<Integer, LinkedHashMap<String, Object>> entry
                : changedVertexMetadata.entrySet()) {
            int vertexId = entry.getKey();
            Map<String, Object> metadata = ImmutableMap.copyOf(entry.getValue());
            if (metadata.isEmpty()) {
                metadataMap = metadataMap.remove(vertexId);
                continue;
            }
            metadataMap = metadataMap.put(vertexId, metadata);
        }
        assertTrue(edgeMap.get(root.getId()).firstTarget(SUEdgeKind.QUERY_TYPE) >= 0,
                "A schema universe schema requires a query type");
        assertOperationTypesRegistered(edgeMap);
        SUSchema schema = new SUSchema(
                universe,
                root,
                namedTypesByNameId,
                edgeMap,
                metadataMap,
                namedTypeCount,
                edgeCount);
        universe.registerSchema(schema);
        return schema;
    }

    private SUSchemaBuilder setOptionalEdge(
            SUEdgeKind kind,
            @Nullable SUObjectType target) {
        if (target == null) {
            return removeEdges(root, kind);
        }
        return setEdge(root, kind, target);
    }

    private void assertOperationTypesRegistered(PersistentEdgeMap edgeMap) {
        assertOperationTypeRegistered(edgeMap, SUEdgeKind.QUERY_TYPE);
        assertOperationTypeRegistered(edgeMap, SUEdgeKind.MUTATION_TYPE);
        assertOperationTypeRegistered(edgeMap, SUEdgeKind.SUBSCRIPTION_TYPE);
    }

    private void retainOperationType(
            PackedEdgeSet rootEdges,
            SUEdgeKind kind) {
        int targetId = rootEdges.firstTarget(kind);
        if (targetId >= 0) {
            addType((SUNamedType) universe.getVertex(targetId));
        }
    }

    private void assertOperationTypeRegistered(
            PersistentEdgeMap edgeMap,
            SUEdgeKind kind) {
        int targetId = edgeMap.get(root.getId()).firstTarget(kind);
        if (targetId < 0) {
            return;
        }
        SUNamedType type = (SUNamedType) universe.getVertex(targetId);
        assertTrue(
                namedTypesByNameId.get(type.getNameId()) == type,
                "Operation type '%s' is not part of the schema",
                assertNotNull(type.getName()));
    }

    private void copyRootEdges(@Nullable SUSchema base) {
        if (base == null) {
            return;
        }
        changedEdges.put(base.getRoot().getId(), new MutablePackedEdgeSet(PackedEdgeSet.empty()));
        changedEdges.put(root.getId(), new MutablePackedEdgeSet(base.getPackedEdges(base.getRoot())));
    }

    private void copyRootMetadata(@Nullable SUSchema base) {
        if (base == null) {
            return;
        }
        Map<String, Object> metadata = base.getVertexMetadata(base.getRoot());
        if (metadata.isEmpty()) {
            return;
        }
        changedVertexMetadata.put(
                base.getRoot().getId(),
                new LinkedHashMap<>());
        changedVertexMetadata.put(
                root.getId(),
                new LinkedHashMap<>(metadata));
    }

    private MutablePackedEdgeSet mutableEdges(SUVertex source) {
        assertOwned(source);
        MutablePackedEdgeSet changed = changedEdges.get(source.getId());
        if (changed != null) {
            return changed;
        }
        PackedEdgeSet original = baseSchema == null
                ? PackedEdgeSet.empty()
                : baseSchema.getPackedEdges(source);
        MutablePackedEdgeSet mutable = new MutablePackedEdgeSet(original);
        changedEdges.put(source.getId(), mutable);
        return mutable;
    }

    private PersistentEdgeMap baseEdgeMap() {
        return baseSchema == null ? PersistentEdgeMap.empty() : baseSchema.getEdgeMap();
    }

    private PersistentIntMap<Map<String, Object>> baseVertexMetadataMap() {
        return baseSchema == null
                ? PersistentIntMap.empty()
                : baseSchema.getVertexMetadataMap();
    }

    private int baseEdgeCount() {
        return baseSchema == null ? 0 : baseSchema.getStoredEdgeCount();
    }

    private LinkedHashMap<String, Object> mutableVertexMetadata(SUVertex vertex) {
        LinkedHashMap<String, Object> changed =
                changedVertexMetadata.get(vertex.getId());
        if (changed != null) {
            return changed;
        }
        LinkedHashMap<String, Object> mutable =
                new LinkedHashMap<>(currentVertexMetadata(vertex));
        changedVertexMetadata.put(vertex.getId(), mutable);
        return mutable;
    }

    private Map<String, Object> currentVertexMetadata(SUVertex vertex) {
        LinkedHashMap<String, Object> changed =
                changedVertexMetadata.get(vertex.getId());
        if (changed != null) {
            return changed;
        }
        return baseSchema == null
                ? ImmutableMap.of()
                : baseSchema.getVertexMetadata(vertex);
    }

    private LinkedHashMap<String, Object> copyMetadata(
            Map<String, Object> metadata) {
        Map<String, Object> nonNullMetadata = assertNotNull(metadata);
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : nonNullMetadata.entrySet()) {
            copy.put(
                    assertNotNull(entry.getKey()),
                    assertNotNull(entry.getValue()));
        }
        return copy;
    }

    private void assertCanChange() {
        assertTrue(!built, "This schema universe builder has already built a schema");
    }

    private void assertOwned(SUVertex vertex) {
        assertTrue(universe.owns(vertex), "Vertex %s belongs to another schema universe", vertex);
    }

    private SUVertex containerVertex(SUAppliedDirectiveContainer container) {
        assertTrue(
                container instanceof SUVertex,
                "Applied directive container must be a schema universe vertex");
        return (SUVertex) container;
    }

    private void assertOutputType(SUType type) {
        assertNotNull(type);
        assertTrue(
                type.getKind() != SUVertexKind.INPUT_OBJECT,
                "Field type must be an output type, found %s",
                type.getKind());
    }

    private void assertInputType(SUType type) {
        assertNotNull(type);
        SUVertexKind kind = type.getKind();
        assertTrue(
                kind != SUVertexKind.OBJECT
                        && kind != SUVertexKind.INTERFACE
                        && kind != SUVertexKind.UNION,
                "Input value type must be an input type, found %s",
                kind);
    }

    private void assertEdgeShape(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        assertOwned(source);
        assertOwned(target);
        if (source.getKind() == SUVertexKind.SCHEMA) {
            assertTrue(source == root, "Only the current schema root can have root edges");
        }
        assertTrue(isValidEdge(source, kind, target), "Invalid %s edge from %s to %s",
                kind, source.getKind(), target.getKind());
    }

    private boolean isValidEdge(
            SUVertex source,
            SUEdgeKind kind,
            SUVertex target) {
        SUVertexKind sourceKind = source.getKind();
        SUVertexKind targetKind = target.getKind();
        switch (kind) {
            case QUERY_TYPE:
            case MUTATION_TYPE:
            case SUBSCRIPTION_TYPE:
                return sourceKind == SUVertexKind.SCHEMA
                        && targetKind == SUVertexKind.OBJECT;
            case DIRECTIVE_DEFINITION:
                return sourceKind == SUVertexKind.SCHEMA
                        && targetKind == SUVertexKind.DIRECTIVE;
            case FIELD:
                return isFieldsContainer(sourceKind) && targetKind == SUVertexKind.FIELD;
            case ARGUMENT:
                return isArgumentsContainer(sourceKind) && targetKind == SUVertexKind.ARGUMENT;
            case TYPE:
                return isTypedElement(sourceKind) && isType(targetKind);
            case IMPLEMENTS:
                return isImplementingType(sourceKind) && targetKind == SUVertexKind.INTERFACE;
            case UNION_MEMBER:
                return sourceKind == SUVertexKind.UNION
                        && targetKind == SUVertexKind.OBJECT;
            case ENUM_VALUE:
                return sourceKind == SUVertexKind.ENUM
                        && targetKind == SUVertexKind.ENUM_VALUE;
            case INPUT_FIELD:
                return sourceKind == SUVertexKind.INPUT_OBJECT
                        && targetKind == SUVertexKind.INPUT_FIELD;
            case APPLIED_DIRECTIVE:
                return isDirectiveContainer(sourceKind)
                        && targetKind == SUVertexKind.APPLIED_DIRECTIVE;
            case WRAPPED_TYPE:
                return isWrappingType(sourceKind) && isType(targetKind)
                        && !(sourceKind == SUVertexKind.NON_NULL
                        && targetKind == SUVertexKind.NON_NULL);
            default:
                return false;
        }
    }

    private boolean isFieldsContainer(SUVertexKind kind) {
        return kind == SUVertexKind.OBJECT || kind == SUVertexKind.INTERFACE;
    }

    private boolean isArgumentsContainer(SUVertexKind kind) {
        return kind == SUVertexKind.FIELD || kind == SUVertexKind.DIRECTIVE;
    }

    private boolean isTypedElement(SUVertexKind kind) {
        return kind == SUVertexKind.FIELD
                || kind == SUVertexKind.ARGUMENT
                || kind == SUVertexKind.INPUT_FIELD;
    }

    private boolean isImplementingType(SUVertexKind kind) {
        return kind == SUVertexKind.OBJECT || kind == SUVertexKind.INTERFACE;
    }

    private boolean isDirectiveContainer(SUVertexKind kind) {
        return kind == SUVertexKind.SCHEMA
                || kind == SUVertexKind.DIRECTIVE
                || kind == SUVertexKind.OBJECT
                || kind == SUVertexKind.FIELD
                || kind == SUVertexKind.INTERFACE
                || kind == SUVertexKind.UNION
                || kind == SUVertexKind.ENUM
                || kind == SUVertexKind.ENUM_VALUE
                || kind == SUVertexKind.SCALAR
                || kind == SUVertexKind.INPUT_OBJECT
                || kind == SUVertexKind.INPUT_FIELD
                || kind == SUVertexKind.ARGUMENT;
    }

    private boolean isWrappingType(SUVertexKind kind) {
        return kind == SUVertexKind.LIST || kind == SUVertexKind.NON_NULL;
    }

    private boolean isNamedType(SUVertexKind kind) {
        return kind == SUVertexKind.OBJECT
                || kind == SUVertexKind.INTERFACE
                || kind == SUVertexKind.UNION
                || kind == SUVertexKind.ENUM
                || kind == SUVertexKind.SCALAR
                || kind == SUVertexKind.INPUT_OBJECT;
    }

    private boolean isType(SUVertexKind kind) {
        return isNamedType(kind) || isWrappingType(kind);
    }
}
