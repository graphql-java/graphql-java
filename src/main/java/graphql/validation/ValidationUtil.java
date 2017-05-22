package graphql.validation;


import graphql.ShouldNotHappenException;
import graphql.language.*;
import graphql.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public boolean isValidLiteralValue(Value value, GraphQLType type) {
        if (value == null) {
            return !(type instanceof GraphQLNonNull);
        }
        if (value instanceof NullValue) {
            return !(type instanceof GraphQLNonNull);
        }
        if (value instanceof VariableReference) {
            return true;
        }
        if (type instanceof GraphQLNonNull) {
            return isValidLiteralValue(value, ((GraphQLNonNull) type).getWrappedType());
        }

        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().parseLiteral(value) != null;
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).getCoercing().parseLiteral(value) != null;
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
        if (!(value instanceof ObjectValue)) return false;
        ObjectValue objectValue = (ObjectValue) value;
        Map<String, ObjectField> objectFieldMap = fieldMap(objectValue);

        if (isFieldMissing(type, objectFieldMap)) return false;

        for (ObjectField objectField : objectValue.getObjectFields()) {
            GraphQLInputObjectField inputObjectField = type.getField(objectField.getName());
            if (inputObjectField == null) return false;
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType())) return false;

        }
        return true;
    }

    private boolean isFieldMissing(GraphQLInputObjectType type, Map<String, ObjectField> objectFieldMap) {
        for (GraphQLInputObjectField inputObjectField : type.getFields()) {
            if (!objectFieldMap.containsKey(inputObjectField.getName()) &&
                    (inputObjectField.getType() instanceof GraphQLNonNull)) return true;
        }
        return false;
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
            for (Value innerValue : ((ArrayValue) value).getValues()) {
                if (!isValidLiteralValue(innerValue, wrappedType)) return false;
            }
            return true;
        } else {
            return isValidLiteralValue(value, wrappedType);
        }
    }

}
