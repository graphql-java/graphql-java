package graphql;


import graphql.schema.GraphQLScalarType;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The standard numerical scalars in {@link Scalars} are strict in implementation and hence a Double
 * passed into a Integer type will not be coerced into that type, whereas these implementations are more lenient
 * and will coerce {@link Number}s into the corresponding scalar values.
 */
@SuppressWarnings("Duplicates")
public class LenientNumericalScalars {

    public static GraphQLScalarType GraphQLInt = new GraphQLScalarType("Int", "Built-in Int", new Scalars.IntegerCoercing() {
        @Override
        public Integer serialize(Object input) {
            if (input instanceof String) {
                return Integer.parseInt((String) input);
            } else if (input instanceof Integer) {
                return (Integer) input;
            } else if (input instanceof Number) {
                return ((Number) input).intValue();
            } else {
                return null;
            }
        }
    });


    public static GraphQLScalarType GraphQLLong = new GraphQLScalarType("Long", "Long type", new Scalars.LongCoercing() {
        @Override
        public Long serialize(Object input) {
            if (input instanceof String) {
                return Long.parseLong((String) input);
            } else if (input instanceof Long) {
                return (Long) input;
            } else if (input instanceof Integer) {
                return ((Integer) input).longValue();
            } else if (input instanceof Number) {
                return ((Number) input).longValue();
            } else {
                return null;
            }
        }
    });

    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Scalars.DoubleCoercing() {
        @Override
        public Double serialize(Object input) {
            if (input instanceof String) {
                return Double.parseDouble((String) input);
            } else if (input instanceof Double) {
                return (Double) input;
            } else if (input instanceof Float) {
                return (double) (Float) input;
            } else if (input instanceof Integer) {
                return (double) (Integer) input;
            } else if (input instanceof Number) {
                return ((Number) input).doubleValue();
            } else {
                return null;
            }
        }
    });


    public static GraphQLScalarType GraphQLBigInteger = new GraphQLScalarType("BigInteger", "Built-in java.math.BigInteger", new Scalars.BigIntegerCoercing() {
        @Override
        public BigInteger serialize(Object input) {
            if (input instanceof BigInteger) {
                return (BigInteger) input;
            } else if (input instanceof String) {
                return new BigInteger((String) input);
            } else if (input instanceof Integer) {
                return BigInteger.valueOf((Integer) input);
            } else if (input instanceof Long) {
                return BigInteger.valueOf((Long) input);
            } else if (input instanceof Number) {
                return BigInteger.valueOf(((Number) input).longValue());
            } else {
                return null;
            }
        }
    });

    public static GraphQLScalarType GraphQLBigDecimal = new GraphQLScalarType("BigDecimal", "Built-in java.math.BigDecimal", new Scalars.BigDecimalCoercing() {
        @Override
        public BigDecimal serialize(Object input) {
            if (input instanceof BigDecimal) {
                return (BigDecimal) input;
            } else if (input instanceof String) {
                return new BigDecimal((String) input);
            } else if (input instanceof Float) {
                return BigDecimal.valueOf((Float) input);
            } else if (input instanceof Double) {
                return BigDecimal.valueOf((Double) input);
            } else if (input instanceof Integer) {
                return BigDecimal.valueOf((Integer) input);
            } else if (input instanceof Long) {
                return BigDecimal.valueOf((Long) input);
            } else if (input instanceof Number) {
                return BigDecimal.valueOf(((Number) input).longValue());
            } else {
                return null;
            }
        }
    });

    public static GraphQLScalarType GraphQLByte = new GraphQLScalarType("Byte", "Built-in Byte as Int", new Scalars.ByteCoercing() {
        @Override
        public Byte serialize(Object input) {
            if (input instanceof String) {
                return Byte.parseByte((String) input);
            } else if (input instanceof Byte) {
                return (Byte) input;
            } else if (input instanceof Number) {
                return ((Number) input).byteValue();
            } else {
                return null;
            }
        }
    });

    public static GraphQLScalarType GraphQLShort = new GraphQLScalarType("Short", "Built-in Short as Int", new Scalars.ShortCoercing() {
        @Override
        public Short serialize(Object input) {
            if (input instanceof String) {
                return Short.parseShort((String) input);
            } else if (input instanceof Short) {
                return (Short) input;
            } else if (input instanceof Number) {
                return ((Number) input).shortValue();
            } else {
                return null;
            }
        }
    });
}
