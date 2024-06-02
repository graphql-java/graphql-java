package graphql.validation;


import com.google.common.collect.ImmutableSet;
import graphql.Assert;
import graphql.Directives;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.CoercedVariables;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    protected void handleNullError(Value<?> value, GraphQLType type) {
    }

    protected void handleScalarError(Value<?> value, GraphQLScalarType type, GraphQLError invalid) {
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

    protected void handleExtraOneOfFieldsError(GraphQLInputObjectType type, Value<?> value) {
    }

    public boolean isValidLiteralValue(Value<?> value, GraphQLType type, GraphQLSchema schema, GraphQLContext graphQLContext, Locale locale) {
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

        if (type instanceof GraphQLScalarType) {
            Optional<GraphQLError> invalid = parseLiteral(value, ((GraphQLScalarType) type).getCoercing(), graphQLContext, locale);
            invalid.ifPresent(graphQLError -> handleScalarError(value, (GraphQLScalarType) type, graphQLError));
            return invalid.isEmpty();
        }
        if (type instanceof GraphQLEnumType) {
            Optional<GraphQLError> invalid = parseLiteralEnum(value, (GraphQLEnumType) type, graphQLContext, locale);
            invalid.ifPresent(graphQLError -> handleEnumError(value, (GraphQLEnumType) type, graphQLError));
            return invalid.isEmpty();
        }

        if (isList(type)) {
            return isValidLiteralValue(value, (GraphQLList) type, schema, graphQLContext, locale);
        }
        return type instanceof GraphQLInputObjectType && isValidLiteralValueForInputObjectType(value, (GraphQLInputObjectType) type, schema, graphQLContext, locale);

    }

    private Optional<GraphQLError> parseLiteralEnum(Value<?> value, GraphQLEnumType graphQLEnumType, GraphQLContext graphQLContext, Locale locale) {
        try {
            graphQLEnumType.parseLiteral(value, graphQLContext, locale);
            return Optional.empty();
        } catch (CoercingParseLiteralException e) {
            return Optional.of(e);
        }
    }

    private Optional<GraphQLError> parseLiteral(Value<?> value, Coercing<?, ?> coercing, GraphQLContext graphQLContext, Locale locale) {
        try {
            coercing.parseLiteral(value, CoercedVariables.emptyVariables(), graphQLContext, locale);
            return Optional.empty();
        } catch (CoercingParseLiteralException e) {
            return Optional.of(e);
        }
    }

    boolean isValidLiteralValueForInputObjectType(Value<?> value, GraphQLInputObjectType type, GraphQLSchema schema, GraphQLContext graphQLContext, Locale locale) {
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
            if (!isValidLiteralValue(objectField.getValue(), inputObjectField.getType(), schema, graphQLContext, locale)) {
                handleFieldNotValidError(objectField, type);
                return false;
            }

        }
        if (type.hasAppliedDirective(Directives.OneOfDirective.getName())) {
            if (objectFieldMap.keySet().size() != 1) {
                handleExtraOneOfFieldsError(type,value);
                return false;
            }
        }
        return true;
    }


    private Set<String> getMissingFields(GraphQLInputObjectType type, Map<String, ObjectField> objectFieldMap, GraphqlFieldVisibility fieldVisibility) {
        return fieldVisibility.getFieldDefinitions(type).stream()
                .filter(field -> isNonNull(field.getType()))
                .filter(value -> (value.getInputFieldDefaultValue().isNotSet()) && !objectFieldMap.containsKey(value.getName()))
                .map(GraphQLInputObjectField::getName)
                .collect(ImmutableSet.toImmutableSet());
    }

    private Map<String, ObjectField> fieldMap(ObjectValue objectValue) {
        Map<String, ObjectField> result = new LinkedHashMap<>();
        for (ObjectField objectField : objectValue.getObjectFields()) {
            result.put(objectField.getName(), objectField);
        }
        return result;
    }

    private boolean isValidLiteralValue(Value<?> value, GraphQLList type, GraphQLSchema schema, GraphQLContext graphQLContext, Locale locale) {
        GraphQLType wrappedType = type.getWrappedType();
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
