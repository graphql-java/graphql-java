package graphql.language;

import graphql.AssertException;
import graphql.Scalars;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AstValueHelper {

    /**
     * Produces a GraphQL Value AST given a Java value.
     *
     * A GraphQL type must be provided, which will be used to interpret different
     * Java values.
     *
     * <pre>
     * |      Value    | GraphQL Value        |
     * | ------------- | -------------------- |
     * | Object        | Input Object         |
     * | Array         | List                 |
     * | Boolean       | Boolean              |
     * | String        | String / Enum Value  |
     * | Number        | Int / Float          |
     * | Mixed         | Enum Value           |
     * </pre>
     *
     * @param value - the java value to be converted into graphql ast
     * @param type  the graphql type of the object
     *
     * @return a grapql language ast {@link Value}
     */
    public static Value astFromValue(Object value, GraphQLType type) {
        if (value == null) {
            return null;
        }

        if (type instanceof GraphQLNonNull) {
            return handleNonNull(value, (GraphQLNonNull) type);
        }

        // Convert JavaScript array to GraphQL list. If the GraphQLType is a list, but
        // the value is not an array, convert the value using the list's item type.
        if (type instanceof GraphQLList) {
            return handleList(value, (GraphQLList) type);
        }

        // Populate the fields of the input object by creating ASTs from each value
        // in the JavaScript object according to the fields in the input type.
        if (type instanceof GraphQLInputObjectType) {
            return handleInputObject(value, (GraphQLInputObjectType) type);
        }

        if (!(type instanceof GraphQLScalarType || type instanceof GraphQLEnumType)) {
            throw new AssertException("Must provide Input Type, cannot use: " + type.getClass());
        }

        // Since value is an internally represented value, it must be serialized
        // to an externally represented value before converting into an AST.
        final Object serialized = serialize(type, value);
        if (isNullish(serialized)) {
            return null;
        }

        // Others serialize based on their corresponding JavaScript scalar types.
        if (serialized instanceof Boolean) {
            return new BooleanValue((Boolean) serialized);
        }

        String stringValue = serialized.toString();
        // numbers can be Int or Float values.
        if (serialized instanceof Number) {
            return handleNumber(stringValue);
        }

        if (serialized instanceof String) {
            // Enum types use Enum literals.
            if (type instanceof GraphQLEnumType) {
                return new EnumValue(stringValue);
            }

            // ID types can use Int literals.
            if (type == Scalars.GraphQLID && stringValue.matches("^[0-9]+$")) {
                return new IntValue(new BigInteger(stringValue));
            }

            // String types are just strings but JSON'ised
            return new StringValue(jsonStringify(stringValue));
        }

        throw new AssertException("'Cannot convert value to AST: " + serialized);
    }

    private static Value handleInputObject(Object _value, GraphQLInputObjectType type) {
        Map mapValue = objToMap(_value);
        List<GraphQLInputObjectField> fields = type.getFields();
        List<ObjectField> fieldNodes = new ArrayList<>();
        fields.forEach(field -> {
            GraphQLInputType fieldType = field.getType();
            Value nodeValue = astFromValue(mapValue.get(field.getName()), fieldType);
            if (nodeValue != null) {

                fieldNodes.add(new ObjectField(field.getName(), nodeValue));
            }
        });
        return new ObjectValue(fieldNodes);
    }

    private static Value handleNumber(String stringValue) {
        if (stringValue.matches("^[0-9]+$")) {
            return new IntValue(new BigInteger(stringValue));
        } else {
            return new FloatValue(new BigDecimal(stringValue));
        }
    }

    private static Value handleList(Object _value, GraphQLList type) {
        GraphQLType itemType = type.getWrappedType();
        if (_value instanceof Iterable) {
            Iterable iterable = (Iterable) _value;
            List<Value> valuesNodes = new ArrayList<>();
            for (Object item : iterable) {
                Value itemNode = astFromValue(item, itemType);
                if (itemNode != null) {
                    valuesNodes.add(itemNode);
                }
            }
            return new ArrayValue(valuesNodes);
        }
        return astFromValue(_value, itemType);
    }

    private static Value handleNonNull(Object _value, GraphQLNonNull type) {
        GraphQLType wrappedType = type.getWrappedType();
        return astFromValue(_value, wrappedType);
    }

    private static String jsonStringify(String stringValue) {
        stringValue = stringValue.replace("\"", "\\\"");
        stringValue = stringValue.replace("\\", "\\\\");
        stringValue = stringValue.replace("/", "\\/");
        stringValue = stringValue.replace("\f", "\\f");
        stringValue = stringValue.replace("\n", "\\n");
        stringValue = stringValue.replace("\r", "\\r");
        stringValue = stringValue.replace("\t", "\\t");
        return stringValue;
    }

    private static Object serialize(GraphQLType type, Object value) {
        if (type instanceof GraphQLScalarType) {
            return ((GraphQLScalarType) type).getCoercing().serialize(value);
        } else {
            return ((GraphQLEnumType) type).getCoercing().serialize(value);
        }
    }

    private static boolean isNullish(Object serialized) {
        if (serialized instanceof Number) {
            return Double.isNaN(((Number) serialized).doubleValue());
        }
        return serialized == null;
    }

    private static Map objToMap(Object value) {
        if (value instanceof Map) {
            return (Map) value;
        }
        // java bean inspector
        Map<String, Object> result = new HashMap<>();
        try {
            BeanInfo info = Introspector.getBeanInfo(value.getClass());
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                Method reader = pd.getReadMethod();
                if (reader != null)
                    result.put(pd.getName(), reader.invoke(value));
            }
        } catch (IntrospectionException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
