package graphql.schema.scalars;

import graphql.Assert;
import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.util.FpKit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.language.ObjectField.newObjectField;

/**
 * Copied from ExtendedScalars to avoid the circular dependency but used for testing as an example of a complex scalar
 */
@Internal
public class ObjectScalar {

    static Coercing<Object, Object> OBJECT_COERCING = new Coercing<Object, Object>() {
        @Override
        public Object serialize(Object input) throws CoercingSerializeException {
            return input;
        }

        @Override
        public Object parseValue(Object input) throws CoercingParseValueException {
            return input;
        }

        @Override
        public Object parseLiteral(Object input) throws CoercingParseLiteralException {
            // on purpose - object scalars can be null
            //noinspection ConstantConditions
            return parseLiteral(input, Collections.emptyMap());
        }

        @Override
        public Object parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
            if (!(input instanceof Value)) {
                throw new CoercingParseLiteralException(
                        "Expected AST type 'Value' but was '" + typeName(input) + "'."
                );
            }
            if (input instanceof NullValue) {
                return null;
            }
            if (input instanceof FloatValue) {
                return ((FloatValue) input).getValue();
            }
            if (input instanceof StringValue) {
                return ((StringValue) input).getValue();
            }
            if (input instanceof IntValue) {
                return ((IntValue) input).getValue();
            }
            if (input instanceof BooleanValue) {
                return ((BooleanValue) input).isValue();
            }
            if (input instanceof EnumValue) {
                return ((EnumValue) input).getName();
            }
            if (input instanceof VariableReference) {
                String varName = ((VariableReference) input).getName();
                return variables.get(varName);
            }
            if (input instanceof ArrayValue) {
                List<Value> values = ((ArrayValue) input).getValues();
                return values.stream()
                        .map(v -> parseLiteral(v, variables))
                        .collect(Collectors.toList());
            }
            if (input instanceof ObjectValue) {
                List<ObjectField> values = ((ObjectValue) input).getObjectFields();
                Map<String, Object> parsedValues = new LinkedHashMap<>();
                values.forEach(fld -> {
                    Object parsedValue = parseLiteral(fld.getValue(), variables);
                    parsedValues.put(fld.getName(), parsedValue);
                });
                return parsedValues;
            }
            return Assert.assertShouldNeverHappen("We have covered all Value types");
        }

        @Override
        public Value<?> valueToLiteral(Object input) {
            if (input == null) {
                return NullValue.newNullValue().build();
            }
            if (input instanceof String) {
                return new StringValue((String) input);
            }
            if (input instanceof Float) {
                return new FloatValue(BigDecimal.valueOf((Float) input));
            }
            if (input instanceof Double) {
                return new FloatValue(BigDecimal.valueOf((Double) input));
            }
            if (input instanceof BigDecimal) {
                return new FloatValue((BigDecimal) input);
            }
            if (input instanceof BigInteger) {
                return new IntValue((BigInteger) input);
            }
            if (input instanceof Number) {
                long l = ((Number) input).longValue();
                return new IntValue(BigInteger.valueOf(l));
            }
            if (input instanceof Boolean) {
                return new BooleanValue((Boolean) input);
            }
            if (FpKit.isIterable(input)) {
                return handleIterable(FpKit.toIterable(input));
            }
            if (input instanceof Map) {
                return handleMap((Map<?, ?>) input);
            }
            throw new UnsupportedOperationException("The ObjectScalar cant handle values of type : " + input.getClass());
        }

        private Value<?> handleMap(Map<?, ?> map) {
            ObjectValue.Builder builder = ObjectValue.newObjectValue();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = String.valueOf(entry.getKey());
                Value<?> value = valueToLiteral(entry.getValue());

                builder.objectField(
                        newObjectField().name(name).value(value).build()
                );
            }
            return builder.build();
        }

        @SuppressWarnings("rawtypes")
        private Value<?> handleIterable(Iterable<?> input) {
            List<Value> values = new ArrayList<>();
            for (Object val : input) {
                values.add(valueToLiteral(val));
            }
            return ArrayValue.newArrayValue().values(values).build();
        }

        String typeName(Object input) {
            if (input == null) {
                return "null";
            }
            return input.getClass().getSimpleName();
        }
    };


    public static GraphQLScalarType OBJECT_SCALAR = GraphQLScalarType.newScalar()
            .name("Object")
            .description("An object scalar")
            .coercing(OBJECT_COERCING)
            .build();
}
