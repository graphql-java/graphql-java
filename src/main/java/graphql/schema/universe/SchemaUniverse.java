package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.InputValueDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.InputValueWithState;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.Assert.assertValidName;
import static graphql.util.Interning.intern;

/**
 * A store of append-only schema vertices shared by many immutable schema snapshots.
 *
 * <p>A universe is safe for concurrent readers. Vertex creation is serialized, while reading a
 * published vertex or schema does not require locking. Schemas can be removed from the universe's
 * registry, but removing a schema does not remove its vertices from the universe.</p>
 */
@ExperimentalApi
@NullMarked
public final class SchemaUniverse {

    private static final int VERTEX_CHUNK_SHIFT = 10;
    private static final int VERTEX_CHUNK_SIZE = 1 << VERTEX_CHUNK_SHIFT;
    private static final int VERTEX_CHUNK_MASK = VERTEX_CHUNK_SIZE - 1;
    private static final int MAX_NAME_ID = 0x00ff_ffff;

    private final Map<String, Integer> nameIds = new ConcurrentHashMap<>();
    private final Map<String, SUSchema> schemasByName = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SUSchema> schemas = new ConcurrentLinkedQueue<>();
    private volatile SUVertex[][] vertexChunks = new SUVertex[1][];
    private volatile int vertexCount;
    private int nextNameId = 1;

    public SUSchemaBuilder newSchema(String name) {
        return new SUSchemaBuilder(this, newSchemaRoot(name, null), null);
    }

    public SUSchemaBuilder newSchema(String name, @Nullable String description) {
        return new SUSchemaBuilder(this, newSchemaRoot(name, description), null);
    }

    public SUSchema importSchema(String name, GraphQLSchema schema) {
        return new SUImporter(this).importSchema(name, schema);
    }

    public SUSchema importSchema(String name, TypeDefinitionRegistry typeDefinitionRegistry) {
        return new SUSchemaGenerator(this).generate(name, typeDefinitionRegistry);
    }

    public SUSchema parseSchema(String name, String sdl) {
        return importSchema(name, new SchemaParser().parse(sdl));
    }

    public @Nullable SUSchema getSchema(String name) {
        return schemasByName.get(assertNotNull(name));
    }

    /**
     * Removes and returns the registered schema with the given name.
     *
     * <p>This only removes the schema from this universe's registry. The schema remains usable if
     * it is referenced elsewhere, and its vertices remain stored in this universe.</p>
     *
     * @return the removed schema, or {@code null} if no schema has that name
     */
    public synchronized @Nullable SUSchema removeSchema(String name) {
        SUSchema removed = schemasByName.remove(assertNotNull(name));
        if (removed != null) {
            assertTrue(schemas.remove(removed), "Schema registry is inconsistent");
        }
        return removed;
    }

    /**
     * Removes the exact schema instance from this universe's registry.
     *
     * <p>If its name has since been reused by another schema, that replacement is not removed.</p>
     *
     * @return {@code true} if the schema was registered and removed
     */
    public synchronized boolean removeSchema(SUSchema schema) {
        SUSchema ownedSchema = assertNotNull(schema);
        assertTrue(ownedSchema.getUniverse() == this, "Schema belongs to another universe");
        boolean removed = schemasByName.remove(ownedSchema.getName(), ownedSchema);
        if (removed) {
            assertTrue(schemas.remove(ownedSchema), "Schema registry is inconsistent");
        }
        return removed;
    }

    public List<SUSchema> getSchemas() {
        return List.copyOf(schemas);
    }

    public Map<String, SUSchema> getSchemasByName() {
        Map<String, SUSchema> result = new LinkedHashMap<>();
        for (SUSchema schema : schemas) {
            result.put(schema.getName(), schema);
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized SUObjectType newObjectType(String name) {
        return newObjectType(name, null);
    }

    public synchronized SUObjectType newObjectType(String name, @Nullable String description) {
        String validName = named(name);
        SUObjectType vertex = new SUObjectType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUField newField(String name) {
        return newField(name, null);
    }

    public synchronized SUField newField(String name, @Nullable String description) {
        String validName = named(name);
        SUField vertex = new SUField(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUInterfaceType newInterfaceType(String name) {
        return newInterfaceType(name, null);
    }

    public synchronized SUInterfaceType newInterfaceType(String name, @Nullable String description) {
        String validName = named(name);
        SUInterfaceType vertex = new SUInterfaceType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUUnionType newUnionType(String name) {
        return newUnionType(name, null);
    }

    public synchronized SUUnionType newUnionType(String name, @Nullable String description) {
        String validName = named(name);
        SUUnionType vertex = new SUUnionType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUEnumType newEnumType(String name) {
        return newEnumType(name, null);
    }

    public synchronized SUEnumType newEnumType(String name, @Nullable String description) {
        String validName = named(name);
        SUEnumType vertex = new SUEnumType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUEnumValue newEnumValue(String name) {
        return newEnumValue(name, null);
    }

    public synchronized SUEnumValue newEnumValue(String name, @Nullable String description) {
        String validName = named(name);
        SUEnumValue vertex = new SUEnumValue(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUScalarType newScalarType(String name) {
        return newScalarType(name, null);
    }

    public synchronized SUScalarType newScalarType(String name, @Nullable String description) {
        String validName = named(name);
        SUScalarType vertex = new SUScalarType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUInputObjectType newInputObjectType(String name) {
        return newInputObjectType(name, null);
    }

    public synchronized SUInputObjectType newInputObjectType(String name, @Nullable String description) {
        String validName = named(name);
        SUInputObjectType vertex = new SUInputObjectType(vertexCount, nameId(validName), validName, description);
        return append(vertex);
    }

    public synchronized SUInputField newInputField(String name) {
        return newInputField(name, null);
    }

    public synchronized SUInputField newInputField(String name, @Nullable String description) {
        return newInputField(name, description, InputValueWithState.NOT_SET, null);
    }

    public synchronized SUInputField newInputField(
            String name,
            @Nullable String description,
            InputValueWithState defaultValue) {
        return newInputField(name, description, defaultValue, null);
    }

    public synchronized SUInputField newInputField(
            String name,
            @Nullable String description,
            InputValueWithState defaultValue,
            @Nullable InputValueDefinition definition) {
        String validName = named(name);
        SUInputField vertex = new SUInputField(
                vertexCount,
                nameId(validName),
                validName,
                description,
                defaultValue,
                definition);
        return append(vertex);
    }

    public synchronized SUArgument newArgument(String name) {
        return newArgument(name, null);
    }

    public synchronized SUArgument newArgument(String name, @Nullable String description) {
        return newArgument(name, description, InputValueWithState.NOT_SET, null);
    }

    public synchronized SUArgument newArgument(
            String name,
            @Nullable String description,
            InputValueWithState defaultValue) {
        return newArgument(name, description, defaultValue, null);
    }

    public synchronized SUArgument newArgument(
            String name,
            @Nullable String description,
            InputValueWithState defaultValue,
            @Nullable InputValueDefinition definition) {
        String validName = named(name);
        SUArgument vertex = new SUArgument(
                vertexCount,
                nameId(validName),
                validName,
                description,
                defaultValue,
                definition);
        return append(vertex);
    }

    public synchronized SUDirective newDirective(String name) {
        return newDirective(name, null);
    }

    public synchronized SUDirective newDirective(String name, @Nullable String description) {
        return newDirective(name, description, false, Collections.emptySet(), null);
    }

    public synchronized SUDirective newDirective(
            String name,
            @Nullable String description,
            boolean repeatable,
            Set<DirectiveLocation> validLocations) {
        return newDirective(name, description, repeatable, validLocations, null);
    }

    public synchronized SUDirective newDirective(
            String name,
            @Nullable String description,
            boolean repeatable,
            Set<DirectiveLocation> validLocations,
            @Nullable DirectiveDefinition definition) {
        String validName = named(name);
        SUDirective vertex = new SUDirective(
                vertexCount,
                nameId(validName),
                validName,
                description,
                repeatable,
                validLocations,
                definition);
        return append(vertex);
    }

    public synchronized SUAppliedDirective newAppliedDirective(String name) {
        return newAppliedDirective(name, Collections.emptyList(), null);
    }

    public synchronized SUAppliedDirective newAppliedDirective(
            String name,
            @Nullable Directive definition) {
        return newAppliedDirective(name, Collections.emptyList(), definition);
    }

    public synchronized SUAppliedDirective newAppliedDirective(
            String name,
            List<SUAppliedDirectiveArgument> arguments) {
        return newAppliedDirective(name, arguments, null);
    }

    public synchronized SUAppliedDirective newAppliedDirective(
            String name,
            List<SUAppliedDirectiveArgument> arguments,
            @Nullable Directive definition) {
        String validName = named(name);
        Set<String> argumentNames = new HashSet<>();
        for (SUAppliedDirectiveArgument argument : assertNotNull(arguments)) {
            assertNotNull(argument);
            assertTrue(
                    argument.getUniverse() == this,
                    "Applied directive argument '%s' belongs to another schema universe",
                    argument.getName());
            assertTrue(
                    argumentNames.add(argument.getName()),
                    "Applied directive contains duplicate argument '%s'",
                    argument.getName());
        }
        SUAppliedDirective vertex =
                new SUAppliedDirective(
                        vertexCount,
                        nameId(validName),
                        validName,
                        definition,
                        arguments);
        return append(vertex);
    }

    public synchronized SUAppliedDirectiveArgument newAppliedDirectiveArgument(
            String name,
            SUType type) {
        return newAppliedDirectiveArgument(name, type, InputValueWithState.NOT_SET, null);
    }

    public synchronized SUAppliedDirectiveArgument newAppliedDirectiveArgument(
            String name,
            SUType type,
            InputValueWithState value) {
        return newAppliedDirectiveArgument(name, type, value, null);
    }

    public synchronized SUAppliedDirectiveArgument newAppliedDirectiveArgument(
            String name,
            SUType type,
            InputValueWithState value,
            @Nullable Argument definition) {
        assertTrue(owns(assertNotNull(type)), "Type vertex %s belongs to another schema universe", type);
        assertTrue(
                isInputType(type.getKind()),
                "Applied directive argument type must be an input type, found %s",
                type.getKind());
        String validName = named(name);
        return new SUAppliedDirectiveArgument(
                this,
                validName,
                type.getId(),
                value,
                definition);
    }

    public synchronized SUListType newListType() {
        return append(new SUListType(vertexCount));
    }

    public synchronized SUNonNullType newNonNullType() {
        return append(new SUNonNullType(vertexCount));
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public SUVertex getVertex(int id) {
        int currentSize = vertexCount;
        assertTrue(id >= 0 && id < currentSize, "Unknown schema universe vertex id %s", id);
        SUVertex[][] chunks = vertexChunks;
        return assertNotNull(chunks[id >>> VERTEX_CHUNK_SHIFT][id & VERTEX_CHUNK_MASK]);
    }

    @Internal
    public int getNameId(String name) {
        Integer nameId = nameIds.get(name);
        return nameId == null ? -1 : nameId;
    }

    @Internal
    public boolean owns(SUVertex vertex) {
        int id = vertex.getId();
        return id >= 0 && id < vertexCount && getVertex(id) == vertex;
    }

    @Internal
    public SUSchemaBuilder transformBuilder(SUSchema schema, String name) {
        assertTrue(schema.getUniverse() == this, "Schema belongs to another universe");
        return new SUSchemaBuilder(
                this,
                newSchemaRoot(name, schema.getRoot().getDescription()),
                schema);
    }

    @Internal
    public synchronized void registerSchema(SUSchema schema) {
        assertTrue(schema.getUniverse() == this, "Schema belongs to another universe");
        String name = schema.getName();
        assertTrue(!schemasByName.containsKey(name),
                "Schema universe already contains a schema named '%s'", name);
        schemas.add(schema);
        schemasByName.put(name, schema);
    }

    private synchronized SUSchemaRoot newSchemaRoot(String name, @Nullable String description) {
        String validName = named(name);
        SUSchemaRoot root = new SUSchemaRoot(vertexCount, nameId(validName), validName, description);
        return append(root);
    }

    private String named(String name) {
        return intern(assertValidName(name));
    }

    private int nameId(String name) {
        Integer existing = nameIds.get(name);
        if (existing != null) {
            return existing;
        }
        assertTrue(nextNameId <= MAX_NAME_ID, "Schema universe has too many distinct names");
        int newNameId = nextNameId++;
        nameIds.put(name, newNameId);
        return newNameId;
    }

    private boolean isInputType(SUVertexKind kind) {
        return kind == SUVertexKind.SCALAR
                || kind == SUVertexKind.ENUM
                || kind == SUVertexKind.INPUT_OBJECT
                || kind == SUVertexKind.LIST
                || kind == SUVertexKind.NON_NULL;
    }

    private <T extends SUVertex> T append(T vertex) {
        int id = vertex.getId();
        int chunkIndex = id >>> VERTEX_CHUNK_SHIFT;
        ensureChunk(chunkIndex);
        vertexChunks[chunkIndex][id & VERTEX_CHUNK_MASK] = vertex;
        vertexCount = id + 1;
        return vertex;
    }

    private void ensureChunk(int chunkIndex) {
        SUVertex[][] chunks = vertexChunks;
        if (chunkIndex >= chunks.length) {
            chunks = Arrays.copyOf(chunks, Math.max(chunkIndex + 1, chunks.length * 2));
            vertexChunks = chunks;
        }
        if (chunks[chunkIndex] == null) {
            chunks[chunkIndex] = new SUVertex[VERTEX_CHUNK_SIZE];
        }
    }
}
