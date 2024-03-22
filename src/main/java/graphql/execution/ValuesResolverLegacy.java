package graphql.execution;

import graphql.AssertException;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.Scalars;
import graphql.VisibleForTesting;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.PropertyDataFetcherHelper;
import graphql.util.FpKit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static graphql.Assert.assertTrue;
import static graphql.language.ObjectField.newObjectField;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static java.util.stream.Collectors.toList;

/*
 * ======================LEGACY=======+TO BE REMOVED IN THE FUTURE ===============
 */

@Internal
class ValuesResolverLegacy {
    /**
     * Legacy logic to convert an arbitrary java object to an Ast Literal.
     * Only provided here to preserve backwards compatibility.
     */
    @VisibleForTesting
    static Value<?> valueToLiteralLegacy(Object value, GraphQLType type, GraphQLContext graphqlContext, Locale locale) {
        assertTrue(!(value instanceof Value), "Unexpected literal %s", value);
        if (value == null) {
            return null;
        }

        if (isNonNull(type)) {
            return handleNonNullLegacy(value, (GraphQLNonNull) type, graphqlContext, locale);
        }

        // Convert JavaScript array to GraphQL list. If the GraphQLType is a list, but
        // the value is not an array, convert the value using the list's item type.
        if (isList(type)) {
            return handleListLegacy(value, (GraphQLList) type, graphqlContext, locale);
        }

        // Populate the fields of the input object by creating ASTs from each value
        // in the JavaScript object according to the fields in the input type.
        if (type instanceof GraphQLInputObjectType) {
            return handleInputObjectLegacy(value, (GraphQLInputObjectType) type, graphqlContext, locale);
        }

        if (!(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType)) {
            throw new AssertException("Must provide Input Type, cannot use: " + type.getClass());
        }

        // Since value is an internally represented value, it must be serialized
        // to an externally represented value before converting into an AST.
        final Object serialized = serializeLegacy(type, value, graphqlContext, locale);

        // Others serialize based on their corresponding JavaScript scalar types.
        if (serialized instanceof Boolean) {
            return BooleanValue.newBooleanValue().value((Boolean) serialized).build();
        }

        String stringValue = serialized.toString();
        // numbers can be Int or Float values.
        if (serialized instanceof Number) {
            return handleNumberLegacy(stringValue);
        }

        if (serialized instanceof String) {
            // Enum types use Enum literals.
            if (type instanceof GraphQLEnumType) {
                return EnumValue.newEnumValue().name(stringValue).build();
            }

            // ID types can use Int literals.
            if (type == Scalars.GraphQLID && stringValue.matches("^[0-9]+$")) {
                return IntValue.newIntValue().value(new BigInteger(stringValue)).build();
            }

            return StringValue.newStringValue().value(stringValue).build();
        }

        throw new AssertException("'Cannot convert value to AST: " + serialized);
    }

    private static Value<?> handleInputObjectLegacy(Object javaValue, GraphQLInputObjectType type, GraphQLContext graphqlContext, Locale locale) {
        List<GraphQLInputObjectField> fields = type.getFields();
        List<ObjectField> fieldNodes = new ArrayList<>();
        fields.forEach(field -> {
            String fieldName = field.getName();
            GraphQLInputType fieldType = field.getType();
            Object fieldValueObj = PropertyDataFetcherHelper.getPropertyValue(fieldName, javaValue, fieldType);
            Value<?> nodeValue = valueToLiteralLegacy(fieldValueObj, fieldType, graphqlContext, locale);
            if (nodeValue != null) {
                fieldNodes.add(newObjectField().name(fieldName).value(nodeValue).build());
            }
        });
        return ObjectValue.newObjectValue().objectFields(fieldNodes).build();
    }

    private static Value<?> handleNumberLegacy(String stringValue) {
        if (stringValue.matches("^[0-9]+$")) {
            return IntValue.newIntValue().value(new BigInteger(stringValue)).build();
        } else {
            return FloatValue.newFloatValue().value(new BigDecimal(stringValue)).build();
        }
    }

    @SuppressWarnings("rawtypes")
    private static Value<?> handleListLegacy(Object value, GraphQLList type, GraphQLContext graphqlContext, Locale locale) {
        GraphQLType itemType = type.getWrappedType();
        if (FpKit.isIterable(value)) {
            List<Value> valuesNodes = FpKit.toListOrSingletonList(value)
                    .stream()
                    .map(item -> valueToLiteralLegacy(item, itemType, graphqlContext, locale))
                    .collect(toList());
            return ArrayValue.newArrayValue().values(valuesNodes).build();
        }
        return valueToLiteralLegacy(value, itemType, graphqlContext, locale);
    }

    private static Value<?> handleNonNullLegacy(Object _value, GraphQLNonNull type, GraphQLContext graphqlContext, Locale locale) {
        GraphQLType wrappedType = type.getWrappedType();
        return valueToLiteralLegacy(_value, wrappedType, graphqlContext, locale);
    }

    private static Object serializeLegacy(GraphQLType type, Object value, GraphQLContext graphqlContext, Locale locale) {
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().serialize(value, graphqlContext, locale);
        } else {
            return ((GraphQLEnumType) type).serialize(value, graphqlContext, locale);
        }
    }
}
