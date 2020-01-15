package graphql.validation;


import graphql.Assert;
import graphql.GraphQLError;
import graphql.language.ArrayValue;
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
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.*;

public class ValidationUtil {

    public static TypeName getUnmodifiedType(Type<?> type) {
        if (type instanceof ListType) {
            return getUnmodifiedType(((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return getUnmodifiedType(((NonNullType) type).getType());
        } else if (type instanceof TypeName) {
            return (TypeName) type;
        }
        return Assert.assertShouldNeverHappen();
    }

    protected void handleNullError(Value<?> value, GraphQLType type) {
    }

    protected void handleScalarError(Value value, GraphQLScalarType type, String message) {
    }

    protected void handleEnumError(Value<?> value, GraphQLEnumType type, GraphQLError invalid) {
    }

    protected void handleNotObjectError(Value<?> value, GraphQLInputObjectType type) {
    }

    protected void handleMissingFieldsError(Value<?> value, GraphQLInputObjectType type, Set<String> missingFields) {
    }

    protected void handleExtraFieldError(Value<?> value, GraphQLInputObjectType type, ObjectField objectField) {
    }

    protected void handleFieldNotValidError(ObjectField objectField, GraphQLInputObjectType type) {
    }

    protected void handleFieldNotValidError(Value<?> value, GraphQLType type, int index) {
    }

    public boolean isValidLiteralValue(Value<?> value, GraphQLType type, GraphQLSchema schema) {
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
            return isValidLiteralValue(value, unwrapOne(type), schema);
        }

        if (type instanceof GraphQLScalarType) {
            Optional<String> errorMessage = parseLiteral(value, ((GraphQLScalarType) type).getCoercing());
            errorMessage.ifPresent(message -> handleScalarError(value, (GraphQLScalarType) type, message));
            return !errorMessage.isPresent();
        }
        if (type instanceof GraphQLEnumType) {
            Optional<String> errorMessage = parseLiteral(value, ((GraphQLEnumType) type).getCoercing());
            if (errorMessage.isPresent()) {
                handleEnumError(value, (GraphQLEnumType) type);
            }
            return !errorMessage.isPresent();
        }

        if (isList(type)) {
            return isValidLiteralValue(value, (GraphQLList) type, schema);
        }
        return type instanceof GraphQLInputObjectType && isValidLiteralValue(value, (GraphQLInputObjectType) type, schema);

    }

    private Optional<String> parseLiteral(Value value, Coercing coercing) {
        try {
            if (coercing.parseLiteral(value) != null) {
                return Optional.empty();
            } else {
                return Optional.of("Failed to parse value");
            }
        } catch (CoercingParseLiteralException e) {
            return Optional.of(e.getMessage());
        }
    }

    private boolean isValidLiteralValue(Value<?> value, GraphQLInputObjectType type, GraphQLSchema schema) {
        if (!(value instanceof ObjectValue)) {
            handleNotObjectError(value, type);
            return false;
        }
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        ObjectValue objectValue = (ObjectValue) value;
        Map<String, ObjectField> objectFieldMap = fieldMap(objectValue);

        Set<String> missingFields = getMissingFields(type, objectFieldMap, fieldVisibility);
        if (!missingFields.isEmpty()) {
            handleMissingFieldsError(value, type, missingFields);
            return false;
        }

        for (ObjectField objectField : objectValue.getObjectFields()) {

            GraphQLInputObjectField inputObjectField = fieldVisibility.getFieldDefinition(type, objectField.getName());
            if (inputObjectField == null) {
                handleExtraFieldError(value, type, objectField);
                return false;
            }
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType(), schema)) {
                handleFieldNotValidError(objectField, type);
                return false;
            }

        }
        return true;
    }

    private Set<String> getMissingFields(GraphQLInputObjectType type, Map<String, ObjectField> objectFieldMap, GraphqlFieldVisibility fieldVisibility) {
        return fieldVisibility.getFieldDefinitions(type).stream()
                .filter(field -> isNonNull(field.getType()))
                .filter(value -> (value.getDefaultValue() == null) && !objectFieldMap.containsKey(value.getName()))
                .map(GraphQLInputObjectField::getName)
                .collect(Collectors.toSet());
    }

    private Map<String, ObjectField> fieldMap(ObjectValue objectValue) {
        Map<String, ObjectField> result = new LinkedHashMap<>();
        for (ObjectField objectField : objectValue.getObjectFields()) {
            result.put(objectField.getName(), objectField);
        }
        return result;
    }

    private boolean isValidLiteralValue(Value<?> value, GraphQLList type, GraphQLSchema schema) {
        GraphQLType wrappedType = type.getWrappedType();
        if (value instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) value).getValues();
            for (int i = 0; i < values.size(); i++) {
                if (!isValidLiteralValue(values.get(i), wrappedType, schema)) {
                    handleFieldNotValidError(values.get(i), wrappedType, i);
                    return false;
                }
            }
            return true;
        } else {
            return isValidLiteralValue(value, wrappedType, schema);
        }
    }

    public static String renderValue(Object value) {
        if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        } else if (value instanceof ArrayValue) {
            return ((ArrayValue) value).getValues().stream()
                    .map(ValidationUtil::renderValue)
                    .collect(Collectors.joining(", ", "[", "]"));
        } else if (value instanceof BooleanValue) {
            return Boolean.toString(((BooleanValue) value).isValue());
        } else if (value instanceof IntValue) {
            return ((IntValue) value).getValue().toString();
        } else if (value instanceof FloatValue) {
            return ((FloatValue) value).getValue().toString();
        } else if (value instanceof NullValue) {
            return "null";
        }
        return value.toString();
    }

    public static String renderType(Object type) {
        if (type instanceof GraphQLNamedSchemaElement) {
            return ((GraphQLNamedSchemaElement) type).getName();
        } else if (type instanceof Type) {
            return getUnmodifiedType(((Type<?>) type)).getName();
        }
        return String.valueOf(type);
    }
}
