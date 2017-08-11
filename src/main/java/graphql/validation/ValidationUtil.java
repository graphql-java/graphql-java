package graphql.validation;


import graphql.ShouldNotHappenException;
import graphql.language.*;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ValidationUtil {

    public TypeName getUnmodifiedType(Type type) {
        if (type instanceof ListType) {
            return getUnmodifiedType(((ListType) type).getType());
        } else if (type instanceof NonNullType) {
            return getUnmodifiedType(((NonNullType) type).getType());
        } else if (type instanceof TypeName) {
            return (TypeName) type;
        }
        throw new ShouldNotHappenException();
    }

    protected void handleNullError(Value value, GraphQLType type) {}

    protected void handleScalarError(Value value, GraphQLScalarType type) {}

    protected void handleEnumError(Value value, GraphQLEnumType type) {}

    protected void handleNotObjectError(Value value, GraphQLInputObjectType type) {}

    protected void handleMissingFieldsError(Value value, GraphQLInputObjectType type, Set<String> missingFields) {}

    protected void handleExtraFieldError(Value value, GraphQLInputObjectType type, ObjectField objectField) {}

    protected void handleFieldNotValidError(ObjectField objectField, GraphQLInputObjectType type) {}

    protected void handleFieldNotValidError(Value value, GraphQLType type, int index) {}

    public boolean isValidLiteralValue(Value value, GraphQLType type) {
        if (value == null || value instanceof NullValue) {
            boolean valid = !(type instanceof GraphQLNonNull);
            if (!valid) {
                handleNullError(value, type);
            }
            return valid;
        }
        if (value instanceof VariableReference) {
            return true;
        }
        if (type instanceof GraphQLNonNull) {
            return isValidLiteralValue(value, ((GraphQLNonNull) type).getWrappedType());
        }

        if (type instanceof GraphQLScalarType) {
            boolean valid = ((GraphQLScalarType) type).getCoercing().parseLiteral(value) != null;
            if (!valid) {
                handleScalarError(value, (GraphQLScalarType) type);
            }
            return valid;
        }
        if (type instanceof GraphQLEnumType) {
            boolean valid = ((GraphQLEnumType) type).getCoercing().parseLiteral(value) != null;
            if (!valid) {
                handleEnumError(value, (GraphQLEnumType) type);
            }
            return valid;
        }

        if (type instanceof GraphQLList) {
            return isValidLiteralValue(value, (GraphQLList) type);
        }
        if (type instanceof GraphQLInputObjectType) {
            return isValidLiteralValue(value, (GraphQLInputObjectType) type);
        }

        return false;
    }

    private boolean isValidLiteralValue(Value value, GraphQLInputObjectType type) {
        if (!(value instanceof ObjectValue)) {
            handleNotObjectError(value, type);
            return false;
        }
        ObjectValue objectValue = (ObjectValue) value;
        Map<String, ObjectField> objectFieldMap = fieldMap(objectValue);

        Set<String> missingFields = getMissingFields(type, objectFieldMap);
        if (!missingFields.isEmpty()) {
            handleMissingFieldsError(value, type, missingFields);
            return false;
        }

        for (ObjectField objectField : objectValue.getObjectFields()) {
            GraphQLInputObjectField inputObjectField = type.getField(objectField.getName());
            if (inputObjectField == null) {
                handleExtraFieldError(value, type, objectField);
                return false;
            }
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType())) {
                handleFieldNotValidError(objectField, type);
                return false;
            }

        }
        return true;
    }

    private Set<String> getMissingFields(GraphQLInputObjectType type, Map<String, ObjectField> objectFieldMap) {
        return type.getFields().stream()
                .filter(field -> field.getType() instanceof GraphQLNonNull)
                .map(GraphQLInputObjectField::getName)
                .filter(((Predicate<String>) objectFieldMap::containsKey).negate())
                .collect(Collectors.toSet());
    }

    private Map<String, ObjectField> fieldMap(ObjectValue objectValue) {
        Map<String, ObjectField> result = new LinkedHashMap<>();
        for (ObjectField objectField : objectValue.getObjectFields()) {
            result.put(objectField.getName(), objectField);
        }
        return result;
    }

    private boolean isValidLiteralValue(Value value, GraphQLList type) {
        GraphQLType wrappedType = type.getWrappedType();
        if (value instanceof ArrayValue) {
            List<Value> values = ((ArrayValue) value).getValues();
            for (int i = 0; i < values.size(); i++) {
                if (!isValidLiteralValue(values.get(i), wrappedType)) {
                    handleFieldNotValidError(values.get(i), wrappedType, i);
                    return false;
                }
            }
            return true;
        } else {
            return isValidLiteralValue(value, wrappedType);
        }
    }

}
