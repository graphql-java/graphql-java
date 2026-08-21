package graphql.schema.universe.view;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.language.EnumValue;
import graphql.language.Value;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.SchemaEnum;
import graphql.schema.universe.SUEnumType;
import graphql.schema.universe.SUEnumValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public final class SUSchemaEnum
        extends AbstractSUSchemaNamedType implements SchemaEnum {

    @Internal
    public SUSchemaEnum(
            SUExecutableSchema executableSchema,
            SUEnumType type) {
        super(executableSchema, type);
    }

    @Override
    public Object serialize(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale) {
        for (SUEnumValue value : enumValues()) {
            Object runtimeValue = runtimeValue(value);
            if (input.equals(runtimeValue)) {
                return assertNotNull(value.getName());
            }
            if (runtimeValue instanceof Enum
                    && input instanceof String
                    && input.equals(((Enum<?>) runtimeValue).name())) {
                return assertNotNull(value.getName());
            }
        }
        if (input instanceof Enum) {
            return serializeJavaEnum((Enum<?>) input, locale);
        }
        throw new CoercingSerializeException(
                i18nMsg(locale, "Enum.badInput", getName(), input));
    }

    private Object serializeJavaEnum(
            Enum<?> input,
            Locale locale) {
        String enumName = input.name();
        for (SUEnumValue value : enumValues()) {
            if (enumName.equals(String.valueOf(runtimeValue(value)))) {
                return assertNotNull(value.getName());
            }
        }
        throw new CoercingSerializeException(
                i18nMsg(locale, "Enum.badInput", getName(), input));
    }

    @Override
    public Object parseValue(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale) {
        SUEnumValue value = enumValue(input.toString());
        if (value != null) {
            return runtimeValue(value);
        }
        throw new CoercingParseValueException(
                i18nMsg(locale, "Enum.badName", getName(), input.toString()));
    }

    @Override
    public Object parseLiteral(
            Value<?> input,
            GraphQLContext graphQLContext,
            Locale locale) {
        if (!(input instanceof EnumValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(
                            locale,
                            "Scalar.unexpectedAstType",
                            "EnumValue",
                            typeName(input)));
        }
        SUEnumValue value = enumValue(((EnumValue) input).getName());
        if (value != null) {
            return runtimeValue(value);
        }
        throw new CoercingParseLiteralException(
                i18nMsg(locale, "Enum.unallowableValue", getName(), input));
    }

    @Override
    public Value<?> valueToLiteral(
            Object input,
            GraphQLContext graphQLContext,
            Locale locale) {
        SUEnumValue value = enumValue(input.toString());
        if (value == null) {
            return assertShouldNeverHappen(
                    i18nMsg(
                            locale,
                            "Enum.badName",
                            getName(),
                            input.toString()));
        }
        return EnumValue.newEnumValue(
                assertNotNull(value.getName())).build();
    }

    @Override
    public List<SUSchemaEnumValue> getValues() {
        List<SUEnumValue> values = enumValues();
        List<SUSchemaEnumValue> result = new ArrayList<>(values.size());
        for (SUEnumValue value : values) {
            result.add(new SUSchemaEnumValue(getExecutableSchema(), value));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public @Nullable SUSchemaEnumValue getValue(String name) {
        SUEnumValue value = enumValue(name);
        if (value == null) {
            return null;
        }
        return new SUSchemaEnumValue(getExecutableSchema(), value);
    }

    private List<SUEnumValue> enumValues() {
        return getExecutableSchema()
                .getSchema()
                .getEnumValues(getEnumTypeVertex());
    }

    private @Nullable SUEnumValue enumValue(String name) {
        return getExecutableSchema()
                .getSchema()
                .getEnumValue(getEnumTypeVertex(), name);
    }

    private Object runtimeValue(SUEnumValue value) {
        Object runtimeValue = getExecutableSchema()
                .getEnumRuntimeValueById()
                .get(value.getId());
        if (runtimeValue != null) {
            return runtimeValue;
        }
        return assertNotNull(value.getName());
    }

    @Internal
    public SUEnumType getEnumTypeVertex() {
        return (SUEnumType) getTypeVertex();
    }
}
