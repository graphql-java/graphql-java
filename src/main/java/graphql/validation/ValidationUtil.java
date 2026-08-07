package graphql.validation;


import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.EnumValue;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.ExecutableSchema;
import graphql.schema.SchemaEnum;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaList;
import graphql.schema.SchemaScalar;
import graphql.schema.SchemaType;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.typeName;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

@Internal
public class ValidationUtil {

    public TypeName getUnmodifiedType(Type<?> type) {
        if (type instanceof ListType) {
            return getUnmodifiedType(((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return getUnmodifiedType(((NonNullType) type).getType());
        } else if (type instanceof TypeName) {
            return (TypeName) type;
        }
        return Assert.assertShouldNeverHappen();
    }

    protected void handleNullError(Value<?> value, SchemaType type) {
    }

    protected void handleScalarError(Value<?> value, SchemaScalar type, GraphQLError invalid) {
    }

    protected void handleEnumError(Value<?> value, SchemaEnum type, GraphQLError invalid) {
    }

    protected void handleNotObjectError(Value<?> value, SchemaInputObject type) {
    }

    protected void handleMissingFieldsError(Value<?> value, SchemaInputObject type, Set<String> missingFields) {
    }

    protected void handleExtraFieldError(Value<?> value, SchemaInputObject type, ObjectField objectField) {
    }

    protected void handleFieldNotValidError(ObjectField objectField, SchemaInputObject type) {
    }

    protected void handleFieldNotValidError(Value<?> value, SchemaType type, int index) {
    }

    protected void handleExtraOneOfFieldsError(SchemaInputObject type, Value<?> value) {
    }

    public boolean isValidLiteralValue(Value<?> value, SchemaType type, ExecutableSchema schema, GraphQLContext graphQLContext, Locale locale) {
        if (value == null || value instanceof NullValue) {
            boolean valid = !(isNonNull(type));
            if (!valid) {
                handleNullError(value, type);
            }
            return valid;
        }
        if (value instanceof VariableReference) {
            return true;
        }
        if (isNonNull(type)) {
            return isValidLiteralValue(value, unwrapOne(type), schema, graphQLContext, locale);
        }

        if (type instanceof SchemaScalar) {
            SchemaScalar scalar = (SchemaScalar) type;
            GraphQLError invalid = parseLiteral(
                    value,
                    schema.getScalarCoercing(scalar),
                    graphQLContext,
                    locale);
            if (invalid != null) {
                handleScalarError(value, scalar, invalid);
                return false;
            }
            return true;
        }
        if (type instanceof SchemaEnum) {
            SchemaEnum enumType = (SchemaEnum) type;
            GraphQLError invalid = parseLiteralEnum(value, enumType, locale);
            if (invalid != null) {
                handleEnumError(value, enumType, invalid);
                return false;
            }
            return true;
        }

        if (isList(type)) {
            return isValidLiteralValue(value, (SchemaList) type, schema, graphQLContext, locale);
        }
        return type instanceof SchemaInputObject
                && isValidLiteralValueForInputObjectType(
                value,
                (SchemaInputObject) type,
                schema,
                graphQLContext,
                locale);
    }

    public boolean isValidLiteralValue(
            Value<?> value,
            Type<?> type,
            ExecutableSchema schema,
            GraphQLContext graphQLContext,
            Locale locale) {
        if (type instanceof NonNullType) {
            if (value == null || value instanceof NullValue) {
                return false;
            }
            return isValidLiteralValue(
                    value,
                    ((NonNullType) type).getType(),
                    schema,
                    graphQLContext,
                    locale);
        }
        if (value == null || value instanceof NullValue
                || value instanceof VariableReference) {
            return true;
        }
        if (type instanceof ListType) {
            Type<?> wrappedType = ((ListType) type).getType();
            if (!(value instanceof ArrayValue)) {
                return isValidLiteralValue(
                        value,
                        wrappedType,
                        schema,
                        graphQLContext,
                        locale);
            }
            for (Value<?> item : ((ArrayValue) value).getValues()) {
                if (!isValidLiteralValue(
                        item,
                        wrappedType,
                        schema,
                        graphQLContext,
                        locale)) {
                    return false;
                }
            }
            return true;
        }
        SchemaType schemaType =
                schema.getType(((TypeName) type).getName());
        if (!(schemaType instanceof SchemaInputType)) {
            return false;
        }
        return isValidLiteralValue(
                value,
                schemaType,
                schema,
                graphQLContext,
                locale);
    }

    private @Nullable GraphQLError parseLiteralEnum(
            Value<?> value,
            SchemaEnum enumType,
            Locale locale) {
        if (!(value instanceof EnumValue)) {
            return new CoercingParseLiteralException(
                    i18nMsg(
                            locale,
                            "Scalar.unexpectedAstType",
                            "EnumValue",
                            typeName(value)));
        }
        if (enumType.getValue(((EnumValue) value).getName()) == null) {
            return new CoercingParseLiteralException(
                    i18nMsg(
                            locale,
                            "Enum.unallowableValue",
                            enumType.getName(),
                            value));
        }
        return null;
    }

    private @Nullable GraphQLError parseLiteral(
            Value<?> value,
            Coercing<?, ?> coercing,
            GraphQLContext graphQLContext,
            Locale locale) {
        try {
            coercing.parseLiteral(value, CoercedVariables.emptyVariables(), graphQLContext, locale);
            return null;
        } catch (CoercingParseLiteralException e) {
            return e;
        }
    }

    boolean isValidLiteralValueForInputObjectType(
            Value<?> value,
            SchemaInputObject type,
            ExecutableSchema schema,
            GraphQLContext graphQLContext,
            Locale locale) {
        if (!(value instanceof ObjectValue)) {
            handleNotObjectError(value, type);
            return false;
        }
        ObjectValue objectValue = (ObjectValue) value;
        Map<String, ObjectField> objectFieldMap = fieldMap(objectValue);

        Set<String> missingFields = getMissingFields(type, objectFieldMap, schema);
        if (!missingFields.isEmpty()) {
            handleMissingFieldsError(value, type, missingFields);
            return false;
        }

        for (ObjectField objectField : objectValue.getObjectFields()) {

            SchemaInputField inputObjectField =
                    schema.getInputField(type, objectField.getName());
            if (inputObjectField == null) {
                handleExtraFieldError(value, type, objectField);
                return false;
            }
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType(), schema, graphQLContext, locale)) {
                handleFieldNotValidError(objectField, type);
                return false;
            }

        }
        if (type.isOneOf()) {
            if (objectFieldMap.keySet().size() != 1) {
                handleExtraOneOfFieldsError(type, value);
                return false;
            }
        }
        return true;
    }


    private Set<String> getMissingFields(
            SchemaInputObject type,
            Map<String, ObjectField> objectFieldMap,
            ExecutableSchema schema) {
        return schema.getInputFields(type).stream()
                .filter(field -> isNonNull(field.getType()))
                .filter(value -> (value.getInputFieldDefaultValue().isNotSet()) && !objectFieldMap.containsKey(value.getName()))
                .map(SchemaInputField::getName)
                .collect(ImmutableSet.toImmutableSet());
    }

    private Map<String, ObjectField> fieldMap(ObjectValue objectValue) {
        Map<String, ObjectField> result = new LinkedHashMap<>();
        for (ObjectField objectField : objectValue.getObjectFields()) {
            result.put(objectField.getName(), objectField);
        }
        return result;
    }

    private boolean isValidLiteralValue(
            Value<?> value,
            SchemaList type,
            ExecutableSchema schema,
            GraphQLContext graphQLContext,
            Locale locale) {
        SchemaType wrappedType = type.getWrappedType();
        if (value instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) value).getValues();
            for (int i = 0; i < values.size(); i++) {
                if (!isValidLiteralValue(values.get(i), wrappedType, schema, graphQLContext, locale)) {
                    handleFieldNotValidError(values.get(i), wrappedType, i);
                    return false;
                }
            }
            return true;
        } else {
            return isValidLiteralValue(value, wrappedType, schema, graphQLContext, locale);
        }
    }

}
