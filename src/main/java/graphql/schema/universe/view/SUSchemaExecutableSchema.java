package graphql.schema.universe.view;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.schema.Coercing;
import graphql.schema.ExecutableSchema;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaComposite;
import graphql.schema.SchemaDirective;
import graphql.schema.SchemaField;
import graphql.schema.SchemaFieldsContainer;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaObject;
import graphql.schema.SchemaOutputType;
import graphql.schema.SchemaScalar;
import graphql.schema.SchemaType;
import graphql.schema.universe.PersistentIntMap;
import graphql.schema.universe.SUCompositeType;
import graphql.schema.universe.SUDirective;
import graphql.schema.universe.SUEnumType;
import graphql.schema.universe.SUField;
import graphql.schema.universe.SUInputField;
import graphql.schema.universe.SUInputObjectType;
import graphql.schema.universe.SUInterfaceType;
import graphql.schema.universe.SUListType;
import graphql.schema.universe.SUNamedType;
import graphql.schema.universe.SUNonNullType;
import graphql.schema.universe.SUObjectType;
import graphql.schema.universe.SUScalarType;
import graphql.schema.universe.SUSchema;
import graphql.schema.universe.SUType;
import graphql.schema.universe.SUUnionType;
import graphql.schema.universe.SUVertex;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;

/**
 * An executable, schema-bound adapter over an {@link SUSchema}.
 *
 * <p>Scalar coercers are held by this view because universe vertices contain only reusable
 * type-system topology. The adapter has the same supported lifetime as its underlying schema.</p>
 */
@ExperimentalApi
public final class SUSchemaExecutableSchema implements ExecutableSchema {

    private final SUSchema schema;
    private final PersistentIntMap<Coercing<?, ?>> coercingByScalarId;

    @Internal
    public SUSchemaExecutableSchema(
            SUSchema schema,
            PersistentIntMap<Coercing<?, ?>> coercingByScalarId) {
        this.schema = assertNotNull(schema);
        this.coercingByScalarId = assertNotNull(coercingByScalarId);
    }

    public static SUSchemaExecutableSchemaBuilder newExecutableSchema(
            SUSchema schema) {
        return new SUSchemaExecutableSchemaBuilder(schema);
    }

    public static SUSchemaExecutableSchemaBuilder newExecutableSchema(
            SUSchemaExecutableSchema executableSchema) {
        return new SUSchemaExecutableSchemaBuilder(executableSchema);
    }

    /**
     * Creates an adapter and copies scalar coercers from the schema that supplied its topology.
     *
     * @param schema the imported universe schema
     * @param graphQLSchema the matching source schema
     *
     * @return the executable universe schema
     */
    public static SUSchemaExecutableSchema fromGraphQLSchema(
            SUSchema schema,
            GraphQLSchema graphQLSchema) {
        return newExecutableSchema(schema)
                .scalarCoercings(graphQLSchema)
                .build();
    }

    public SUSchemaExecutableSchema transform(
            Consumer<SUSchemaExecutableSchemaBuilder> builderConsumer) {
        SUSchemaExecutableSchemaBuilder builder =
                newExecutableSchema(this);
        assertNotNull(builderConsumer).accept(builder);
        return builder.build();
    }

    public SUSchema getSchema() {
        return schema;
    }

    @Override
    public SchemaObject getQueryType() {
        return new SUSchemaObject(this, schema.getQueryType());
    }

    @Override
    public @Nullable SchemaObject getMutationType() {
        SUObjectType type = schema.getMutationType();
        if (type == null) {
            return null;
        }
        return new SUSchemaObject(this, type);
    }

    @Override
    public @Nullable SchemaObject getSubscriptionType() {
        SUObjectType type = schema.getSubscriptionType();
        if (type == null) {
            return null;
        }
        return new SUSchemaObject(this, type);
    }

    @Override
    public @Nullable SchemaType getType(String name) {
        SUNamedType type = schema.getType(assertNotNull(name));
        if (type == null) {
            return null;
        }
        return adaptType(type);
    }

    @Override
    public @Nullable SchemaDirective getDirective(String name) {
        SUDirective directive = schema.getDirectiveDefinition(
                assertNotNull(name));
        if (directive == null) {
            return null;
        }
        return new SUSchemaDirective(this, directive);
    }

    @Override
    public SchemaObject getIntrospectionSchemaType() {
        return new SUSchemaObject(
                this,
                schema.getIntrospectionSchemaType());
    }

    @Override
    public @Nullable SchemaField getField(
            SchemaComposite parentType,
            String fieldName) {
        SUVertex parentVertex = elementVertex(parentType);
        String name = assertNotNull(fieldName);
        SchemaField introspectionField = parentVertex == schema.getQueryType()
                ? getQueryIntrospectionField(name)
                : null;
        if (introspectionField != null) {
            return introspectionField;
        }
        if (name.equals(Introspection.TypeNameMetaFieldDef.getName())) {
            return new SUSchemaIntrospectionField(
                    this,
                    Introspection.TypeNameMetaFieldDef);
        }
        SUField field = getFieldVertex(parentVertex, name);
        if (field == null) {
            return null;
        }
        return new SUSchemaField(this, field);
    }

    private @Nullable SchemaField getQueryIntrospectionField(String name) {
        if (name.equals(Introspection.SchemaMetaFieldDef.getName())) {
            return new SUSchemaIntrospectionField(
                    this,
                    Introspection.SchemaMetaFieldDef,
                    new SUSchemaSyntheticNonNull(
                            getIntrospectionSchemaType()));
        }
        if (name.equals(Introspection.TypeMetaFieldDef.getName())) {
            return new SUSchemaIntrospectionField(
                    this,
                    Introspection.TypeMetaFieldDef,
                    introspectionType());
        }
        return null;
    }

    private SchemaOutputType introspectionType() {
        SUObjectType schemaType = schema.getIntrospectionSchemaType();
        SUField typesField = assertNotNull(
                schema.getField(schemaType, "types"),
                "The introspection schema type must declare a 'types' field");
        SUType type = assertNotNull(schema.getType(typesField));
        return adaptOutputType(unwrap(type));
    }

    private SUType unwrap(SUType type) {
        SUType current = type;
        while (current instanceof SUListType
                || current instanceof SUNonNullType) {
            current = wrappedType(current);
        }
        return current;
    }

    private SUType wrappedType(SUType type) {
        if (type instanceof SUListType) {
            return assertNotNull(
                    schema.getWrappedType((SUListType) type));
        }
        return assertNotNull(
                schema.getWrappedType((SUNonNullType) type));
    }

    private @Nullable SUField getFieldVertex(
            SUVertex parentVertex,
            String fieldName) {
        if (parentVertex instanceof SUObjectType) {
            return schema.getField(
                    (SUObjectType) parentVertex,
                    fieldName);
        }
        if (parentVertex instanceof SUInterfaceType) {
            return schema.getField(
                    (SUInterfaceType) parentVertex,
                    fieldName);
        }
        return null;
    }

    @Override
    public List<SchemaField> getFields(
            SchemaFieldsContainer parentType) {
        SUVertex parentVertex = elementVertex(parentType);
        List<SUField> fields = getFieldVertices(parentVertex);
        List<SchemaField> result = new ArrayList<>(fields.size());
        for (SUField field : fields) {
            result.add(new SUSchemaField(this, field));
        }
        return Collections.unmodifiableList(result);
    }

    private List<SUField> getFieldVertices(SUVertex parentVertex) {
        if (parentVertex instanceof SUObjectType) {
            return schema.getFields((SUObjectType) parentVertex);
        }
        assertTrue(
                parentVertex instanceof SUInterfaceType,
                "The field container must belong to this SUSchema");
        return schema.getFields((SUInterfaceType) parentVertex);
    }

    @Override
    public @Nullable SchemaInputField getInputField(
            SchemaInputObject parentType,
            String fieldName) {
        SUInputObjectType type = inputObjectVertex(parentType);
        SUInputField field = schema.getInputField(
                type,
                assertNotNull(fieldName));
        if (field == null) {
            return null;
        }
        return new SUSchemaInputField(this, field);
    }

    @Override
    public List<SchemaInputField> getInputFields(
            SchemaInputObject parentType) {
        List<SUInputField> fields = schema.getInputFields(
                inputObjectVertex(parentType));
        List<SchemaInputField> result =
                new ArrayList<>(fields.size());
        for (SUInputField field : fields) {
            result.add(new SUSchemaInputField(this, field));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<SchemaObject> getPossibleTypes(
            SchemaComposite compositeType) {
        SUCompositeType type = compositeVertex(compositeType);
        List<SUObjectType> possibleTypes = schema.getPossibleTypes(type);
        List<SchemaObject> result =
                new ArrayList<>(possibleTypes.size());
        for (SUObjectType possibleType : possibleTypes) {
            result.add(new SUSchemaObject(this, possibleType));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean isPossibleType(
            SchemaComposite compositeType,
            SchemaObject objectType) {
        return schema.isPossibleType(
                compositeVertex(compositeType),
                objectVertex(objectType));
    }

    @Override
    public Coercing<?, ?> getScalarCoercing(SchemaScalar scalarType) {
        SUVertex vertex = elementVertex(scalarType);
        assertTrue(
                vertex instanceof SUScalarType,
                "The scalar type must belong to this SUSchema");
        return assertNotNull(
                coercingByScalarId.get(vertex.getId()),
                "No coercing is registered for scalar '%s'",
                assertNotNull(vertex.getName()));
    }

    @Internal
    public SchemaType adaptType(SUType type) {
        if (type instanceof SUObjectType) {
            return new SUSchemaObject(this, (SUObjectType) type);
        }
        if (type instanceof SUInterfaceType) {
            return new SUSchemaInterface(this, (SUInterfaceType) type);
        }
        if (type instanceof SUUnionType) {
            return new SUSchemaUnion(this, (SUUnionType) type);
        }
        if (type instanceof SUScalarType) {
            return new SUSchemaScalar(this, (SUScalarType) type);
        }
        if (type instanceof SUEnumType) {
            return new SUSchemaEnum(this, (SUEnumType) type);
        }
        if (type instanceof SUInputObjectType) {
            return new SUSchemaInputObject(
                    this,
                    (SUInputObjectType) type);
        }
        if (type instanceof SUListType) {
            return new SUSchemaList(this, (SUListType) type);
        }
        if (type instanceof SUNonNullType) {
            return new SUSchemaNonNull(this, (SUNonNullType) type);
        }
        return assertShouldNeverHappen(
                "Unsupported schema universe type %s",
                type.getClass().getName());
    }

    @Internal
    public SchemaInputType adaptInputType(SUType type) {
        SchemaType adapted = adaptType(type);
        assertTrue(
                adapted instanceof SchemaInputType,
                "Expected an input type but found %s",
                type);
        return (SchemaInputType) adapted;
    }

    @Internal
    public SchemaOutputType adaptOutputType(SUType type) {
        SchemaType adapted = adaptType(type);
        assertTrue(
                adapted instanceof SchemaOutputType,
                "Expected an output type but found %s",
                type);
        return (SchemaOutputType) adapted;
    }

    @Internal
    public SchemaInputType adaptGraphQLInputType(GraphQLType type) {
        SchemaType adapted = adaptGraphQLType(type);
        assertTrue(
                adapted instanceof SchemaInputType,
                "Expected an input type but found %s",
                type);
        return (SchemaInputType) adapted;
    }

    @Internal
    public SchemaOutputType adaptGraphQLOutputType(GraphQLType type) {
        SchemaType adapted = adaptGraphQLType(type);
        assertTrue(
                adapted instanceof SchemaOutputType,
                "Expected an output type but found %s",
                type);
        return (SchemaOutputType) adapted;
    }

    private SchemaType adaptGraphQLType(GraphQLType type) {
        if (type instanceof GraphQLNonNull) {
            return new SUSchemaSyntheticNonNull(
                    adaptGraphQLType(
                            ((GraphQLNonNull) type).getWrappedType()));
        }
        assertTrue(
                type instanceof GraphQLNamedType,
                "Expected a named GraphQL type");
        String name = ((GraphQLNamedType) type).getName();
        return assertNotNull(
                getType(name),
                "No type named '%s' exists in this SUSchema",
                name);
    }

    private SUVertex elementVertex(Object element) {
        assertTrue(
                element instanceof AbstractSUSchemaElement,
                "The schema element must belong to a SUSchema");
        AbstractSUSchemaElement adapter =
                (AbstractSUSchemaElement) element;
        assertTrue(
                adapter.getExecutableSchema().getSchema() == schema,
                "The schema element must belong to this SUSchema");
        return adapter.getVertex();
    }

    private SUCompositeType compositeVertex(
            SchemaComposite compositeType) {
        SUVertex vertex = elementVertex(compositeType);
        assertTrue(
                vertex instanceof SUCompositeType,
                "Expected a composite type");
        return (SUCompositeType) vertex;
    }

    private SUObjectType objectVertex(SchemaObject objectType) {
        SUVertex vertex = elementVertex(objectType);
        assertTrue(
                vertex instanceof SUObjectType,
                "Expected an object type");
        return (SUObjectType) vertex;
    }

    private SUInputObjectType inputObjectVertex(
            SchemaInputObject inputObjectType) {
        SUVertex vertex = elementVertex(inputObjectType);
        assertTrue(
                vertex instanceof SUInputObjectType,
                "Expected an input object type");
        return (SUInputObjectType) vertex;
    }

    @Internal
    public PersistentIntMap<Coercing<?, ?>> getCoercingByScalarId() {
        return coercingByScalarId;
    }
}
