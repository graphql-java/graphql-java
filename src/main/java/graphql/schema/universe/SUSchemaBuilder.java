package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
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
    private boolean built;

    @Internal
    public SUSchemaBuilder(
            SchemaUniverse universe,
            SUSchemaRoot root,
            @Nullable SUSchema baseSchema) {
        this.universe = assertNotNull(universe);
        this.root = assertNotNull(root);
        this.baseSchema = baseSchema;
        copyRootEdges(baseSchema);
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

    public SUSchemaBuilder additionalType(SUNamedType type) {
        return addAdditionalType(type);
    }

    public SUSchemaBuilder addAdditionalType(SUNamedType type) {
        return addEdge(root, SUEdgeKind.ADDITIONAL_TYPE, type);
    }

    public SUSchemaBuilder removeAdditionalType(SUNamedType type) {
        return removeEdge(root, SUEdgeKind.ADDITIONAL_TYPE, type);
    }

    public SUSchemaBuilder removeAdditionalType(String name) {
        return removeEdge(root, SUEdgeKind.ADDITIONAL_TYPE, name);
    }

    public SUSchemaBuilder clearAdditionalTypes() {
        return removeEdges(root, SUEdgeKind.ADDITIONAL_TYPE);
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
        MutablePackedEdgeSet edges = mutableEdges(source);
        edges.removeKind(kind);
        edges.add(PackedEdgeSet.pack(kind, target.getNameId(), target.getId()));
        return this;
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
        int edgeCount = baseEdgeCount();
        for (Map.Entry<Integer, MutablePackedEdgeSet> entry : changedEdges.entrySet()) {
            int sourceId = entry.getKey();
            PackedEdgeSet oldEdges = edgeMap.get(sourceId);
            PackedEdgeSet newEdges = entry.getValue().freeze();
            edgeCount += newEdges.size() - oldEdges.size();
            edgeMap = edgeMap.put(sourceId, newEdges);
        }
        assertTrue(edgeMap.get(root.getId()).firstTarget(SUEdgeKind.QUERY_TYPE) >= 0,
                "A schema universe schema requires a query type");
        SUSchema schema = new SUSchema(universe, root, edgeMap, edgeCount);
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

    private void copyRootEdges(@Nullable SUSchema base) {
        if (base == null) {
            return;
        }
        changedEdges.put(base.getRoot().getId(), new MutablePackedEdgeSet(PackedEdgeSet.empty()));
        changedEdges.put(root.getId(), new MutablePackedEdgeSet(base.getPackedEdges(base.getRoot())));
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

    private int baseEdgeCount() {
        return baseSchema == null ? 0 : baseSchema.getStoredEdgeCount();
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
            case ADDITIONAL_TYPE:
                return sourceKind == SUVertexKind.SCHEMA && isNamedType(targetKind);
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
