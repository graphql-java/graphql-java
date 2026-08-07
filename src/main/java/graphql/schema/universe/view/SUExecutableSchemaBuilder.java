package graphql.schema.universe.view;

import graphql.ExperimentalApi;
import graphql.schema.Coercing;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.universe.PersistentIntMap;
import graphql.schema.universe.SUEnumType;
import graphql.schema.universe.SUEnumValue;
import graphql.schema.universe.SUNamedType;
import graphql.schema.universe.SUScalarType;
import graphql.schema.universe.SUSchema;
import org.jspecify.annotations.NullUnmarked;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

/**
 * Builds an executable view over an {@link SUSchema}.
 */
@ExperimentalApi
@NullUnmarked
public final class SUExecutableSchemaBuilder {

    private final SUSchema schema;
    private PersistentIntMap<Coercing<?, ?>> coercingByScalarId;
    private PersistentIntMap<Object> enumRuntimeValueById;

    public SUExecutableSchemaBuilder(SUSchema schema) {
        this.schema = assertNotNull(schema);
        this.coercingByScalarId = PersistentIntMap.empty();
        this.enumRuntimeValueById = PersistentIntMap.empty();
    }

    public SUExecutableSchemaBuilder(
            SUExecutableSchema executableSchema) {
        this.schema = assertNotNull(executableSchema).getSchema();
        this.coercingByScalarId = executableSchema.getCoercingByScalarId();
        this.enumRuntimeValueById =
                executableSchema.getEnumRuntimeValueById();
    }

    public SUExecutableSchemaBuilder scalarCoercing(
            SUScalarType scalarType,
            Coercing<?, ?> coercing) {
        SUScalarType scalar = assertNotNull(scalarType);
        String scalarName = assertNotNull(scalar.getName());
        assertTrue(
                schema.getScalarType(scalarName) == scalar,
                "The scalar type must belong to this SUSchema");
        coercingByScalarId = coercingByScalarId.put(
                scalar.getId(),
                assertNotNull(coercing));
        return this;
    }

    public SUExecutableSchemaBuilder scalarCoercing(
            String scalarName,
            Coercing<?, ?> coercing) {
        SUScalarType scalarType = schema.getScalarType(
                assertNotNull(scalarName));
        assertTrue(
                scalarType != null,
                "No scalar type named '%s' exists in this SUSchema",
                scalarName);
        return scalarCoercing(
                assertNotNull(scalarType),
                coercing);
    }

    /**
     * Copies scalar coercers from a matching {@link GraphQLSchema} by scalar name.
     *
     * @param graphQLSchema the source executable schema
     *
     * @return this builder
     */
    public SUExecutableSchemaBuilder scalarCoercings(
            GraphQLSchema graphQLSchema) {
        GraphQLSchema source = assertNotNull(graphQLSchema);
        for (SUNamedType type : schema.getTypes()) {
            copyScalarCoercing(source, type);
        }
        return this;
    }

    private void copyScalarCoercing(
            GraphQLSchema source,
            SUNamedType type) {
        if (!(type instanceof SUScalarType)) {
            return;
        }
        GraphQLType sourceType = source.getType(
                assertNotNull(type.getName()));
        assertTrue(
                sourceType instanceof GraphQLScalarType,
                "The GraphQLSchema has no matching scalar named '%s'",
                type.getName());
        scalarCoercing(
                (SUScalarType) type,
                ((GraphQLScalarType) sourceType).getCoercing());
    }

    public SUExecutableSchemaBuilder enumRuntimeValue(
            SUEnumType enumType,
            String enumValueName,
            Object runtimeValue) {
        SUEnumType type = assertNotNull(enumType);
        String typeName = assertNotNull(type.getName());
        Object value = assertNotNull(
                runtimeValue,
                "runtimeValue can't be null");
        assertTrue(
                schema.getEnumType(typeName) == type,
                "The enum type must belong to this SUSchema");
        SUEnumValue enumValue = schema.getEnumValue(
                type,
                assertNotNull(enumValueName));
        assertTrue(
                enumValue != null,
                "No enum value named '%s.%s' exists in this SUSchema",
                typeName,
                enumValueName);
        if (enumValueName.equals(value)) {
            enumRuntimeValueById = enumRuntimeValueById.remove(
                    assertNotNull(enumValue).getId());
            return this;
        }
        enumRuntimeValueById = enumRuntimeValueById.put(
                assertNotNull(enumValue).getId(),
                value);
        return this;
    }

    /**
     * Copies enum runtime values from a matching {@link GraphQLSchema}.
     *
     * @param graphQLSchema the source executable schema
     *
     * @return this builder
     */
    public SUExecutableSchemaBuilder enumRuntimeValues(
            GraphQLSchema graphQLSchema) {
        GraphQLSchema source = assertNotNull(graphQLSchema);
        for (SUNamedType type : schema.getTypes()) {
            copyEnumRuntimeValues(source, type);
        }
        return this;
    }

    private void copyEnumRuntimeValues(
            GraphQLSchema source,
            SUNamedType type) {
        if (!(type instanceof SUEnumType)) {
            return;
        }
        GraphQLType sourceType = source.getType(
                assertNotNull(type.getName()));
        assertTrue(
                sourceType instanceof GraphQLEnumType,
                "The GraphQLSchema has no matching enum named '%s'",
                type.getName());
        GraphQLEnumType sourceEnum = (GraphQLEnumType) sourceType;
        for (SUEnumValue enumValue :
                schema.getEnumValues((SUEnumType) type)) {
            GraphQLEnumValueDefinition sourceValue = assertNotNull(
                    sourceEnum.getValue(
                            assertNotNull(enumValue.getName())),
                    "The GraphQLSchema has no matching enum value named '%s.%s'",
                    type.getName(),
                    enumValue.getName());
            enumRuntimeValue(
                    (SUEnumType) type,
                    assertNotNull(enumValue.getName()),
                    sourceValue.getValue() == null
                            ? assertNotNull(sourceValue.getName())
                            : sourceValue.getValue());
        }
    }

    public SUExecutableSchema build() {
        return new SUExecutableSchema(
                schema,
                coercingByScalarId,
                enumRuntimeValueById);
    }
}
