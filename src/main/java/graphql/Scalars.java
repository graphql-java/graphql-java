package graphql;


import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Scalars {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger BYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);
    private static final BigInteger BYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);
    private static final BigInteger SHORT_MAX = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger SHORT_MIN = BigInteger.valueOf(Short.MIN_VALUE);


    private static boolean isNumberIsh(Object input) {
        if (input instanceof Number) {
            return true;
        }
        if (input instanceof String) {
            return true;
        }
        return false;
    }

    public static GraphQLScalarType GraphQLInt = new GraphQLScalarType("Int", "Built-in Int", new Coercing<Integer, Integer>() {

        @Override
        public Integer serialize(Object input) {
            if (input instanceof Integer) {
                return (Integer) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for Int");
                }
                try {
                    return value.intValueExact();
                } catch (ArithmeticException e) {
                    throw new GraphQLException("Invalid input " + input + " for Int");
                }
            } else {
                throw new GraphQLException("Invalid input " + input + " for Int");
            }
        }

        @Override
        public Integer parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Integer parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            BigInteger value = ((IntValue) input).getValue();
            if (value.compareTo(INT_MIN) == -1 || value.compareTo(INT_MAX) == 1) {
                return null;
            }
            return value.intValue();
        }
    });

    public static GraphQLScalarType GraphQLLong = new GraphQLScalarType("Long", "Long type", new Coercing<Long, Long>() {
        @Override
        public Long serialize(Object input) {
            if (input instanceof Long) {
                return (Long) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for Long");
                }
                try {
                    return value.longValueExact();
                } catch (ArithmeticException e) {
                    throw new GraphQLException("Invalid input " + input + " for Long");
                }
            } else {
                throw new GraphQLException("Invalid input " + input + " for Int");
            }
        }

        @Override
        public Long parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Long parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return Long.parseLong(((StringValue) input).getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                BigInteger value = ((IntValue) input).getValue();
                if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
                    return null;
                }
                return value.longValue();
            }
            return null;
        }
    });

    public static GraphQLScalarType GraphQLShort = new GraphQLScalarType("Short", "Built-in Short as Int", new Coercing<Short, Short>() {
        @Override
        public Short serialize(Object input) {
            if (input instanceof Short) {
                return (Short) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for Short");
                }
                try {
                    return value.shortValueExact();
                } catch (ArithmeticException e) {
                    throw new GraphQLException("Invalid input " + input + " for Short");
                }
            } else {
                throw new GraphQLException("Invalid input " + input + " for Short");
            }
        }

        @Override
        public Short parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Short parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            BigInteger value = ((IntValue) input).getValue();
            if (value.compareTo(SHORT_MIN) < 0 || value.compareTo(SHORT_MAX) > 0) {
                return null;
            }
            return value.shortValue();
        }
    });

    public static GraphQLScalarType GraphQLByte = new GraphQLScalarType("Byte", "Built-in Byte as Int", new Coercing<Byte, Byte>() {
        @Override
        public Byte serialize(Object input) {
            if (input instanceof Byte) {
                return (Byte) input;
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for Byte");
                }
                try {
                    return value.byteValueExact();
                } catch (ArithmeticException e) {
                    throw new GraphQLException("Invalid input " + input + " for Byte");
                }
            } else {
                throw new GraphQLException("Invalid input " + input + " for Byte");
            }
        }

        @Override
        public Byte parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Byte parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            BigInteger value = ((IntValue) input).getValue();
            if (value.compareTo(BYTE_MIN) < 0 || value.compareTo(BYTE_MAX) > 0) {
                return null;
            }
            return value.byteValue();
        }
    });


    /**
     * Note: The Float type in GraphQL is equivalent to Double in Java. (double precision IEEE 754)
     */
    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Coercing<Double, Double>() {
        @Override
        public Double serialize(Object input) {
            if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for Byte");
                }
                return value.doubleValue();
            } else {
                throw new GraphQLException("Invalid input " + input + " for Byte");
            }
        }

        @Override
        public Double parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Double parseLiteral(Object input) {
            if (input instanceof IntValue) {
                return ((IntValue) input).getValue().doubleValue();
            } else if (input instanceof FloatValue) {
                return ((FloatValue) input).getValue().doubleValue();
            } else {
                return null;
            }
        }
    });

    public static GraphQLScalarType GraphQLBigInteger = new GraphQLScalarType("BigInteger", "Built-in java.math.BigInteger", new Coercing<BigInteger, BigInteger>() {
        @Override
        public BigInteger serialize(Object input) {
            if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for BigDecimal");
                }
                try {
                    return value.toBigIntegerExact();
                } catch (ArithmeticException e) {
                    throw new GraphQLException("Invalid input " + input + " for BigDecimal");
                }
            }
            throw new GraphQLException("Invalid input " + input + " for BigDecimal");
        }

        @Override
        public BigInteger parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public BigInteger parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return new BigDecimal(((StringValue) input).getValue()).toBigIntegerExact();
                } catch (NumberFormatException | ArithmeticException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                return ((IntValue) input).getValue();
            } else if (input instanceof FloatValue) {
                try {
                    return ((FloatValue) input).getValue().toBigIntegerExact();
                } catch (ArithmeticException e) {
                    return null;
                }
            }
            return null;
        }
    });

    public static GraphQLScalarType GraphQLBigDecimal = new GraphQLScalarType("BigDecimal", "Built-in java.math.BigDecimal", new Coercing<BigDecimal, BigDecimal>() {
        @Override
        public BigDecimal serialize(Object input) {
            if (isNumberIsh(input)) {
                try {
                    return new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    throw new GraphQLException("Invalid input " + input + " for BigDecimal");
                }
            }
            throw new GraphQLException("Invalid input " + input + " for BigDecimal");
        }

        @Override
        public BigDecimal parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public BigDecimal parseLiteral(Object input) {
            if (input instanceof StringValue) {
                try {
                    return new BigDecimal(((StringValue) input).getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (input instanceof IntValue) {
                return new BigDecimal(((IntValue) input).getValue());
            } else if (input instanceof FloatValue) {
                return ((FloatValue) input).getValue();
            }
            return null;
        }
    });


    public static GraphQLScalarType GraphQLString = new GraphQLScalarType("String", "Built-in String", new Coercing<String, String>() {
        @Override
        public String serialize(Object input) {
            return input.toString();
        }

        @Override
        public String parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public String parseLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            return ((StringValue) input).getValue();
        }
    });


    public static GraphQLScalarType GraphQLBoolean = new GraphQLScalarType("Boolean", "Built-in Boolean", new Coercing<Boolean, Boolean>() {
        @Override
        public Boolean serialize(Object input) {
            if (input instanceof Boolean) {
                return (Boolean) input;
            } else if (input instanceof String) {
                return Boolean.parseBoolean((String) input);
            } else if (isNumberIsh(input)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(input.toString());
                } catch (NumberFormatException e) {
                    // this should never happen because String is handled above
                    throw new GraphQLException("Invalid input " + input + " for Boolean");
                }
                return value.compareTo(BigDecimal.ZERO) != 0;
            } else {
                throw new GraphQLException("Invalid input " + input + " for Boolean");
            }
        }

        @Override
        public Boolean parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Boolean parseLiteral(Object input) {
            if (!(input instanceof BooleanValue)) return null;
            return ((BooleanValue) input).isValue();
        }
    });


    public static GraphQLScalarType GraphQLID = new GraphQLScalarType("ID", "Built-in ID", new Coercing<Object, Object>() {
        @Override
        public String serialize(Object input) {
            if (input instanceof String) {
                return (String) input;
            }
            if (input instanceof Integer) {
                return String.valueOf(input);
            }
            throw new GraphQLException("Invalid input " + input + " for ID");
        }

        @Override
        public String parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public String parseLiteral(Object input) {
            if (input instanceof StringValue) {
                return ((StringValue) input).getValue();
            }
            if (input instanceof IntValue) {
                return ((IntValue) input).getValue().toString();
            }
            return null;
        }
    });


    public static GraphQLScalarType GraphQLChar = new GraphQLScalarType("Char", "Built-in Char as Character", new Coercing<Character, Character>() {
        @Override
        public Character serialize(Object input) {
            if (input instanceof String && ((String) input).length() == 1) {
                return ((String) input).charAt(0);
            } else if (input instanceof Character) {
                return (Character) input;
            } else {
                throw new GraphQLException("Invalid input " + input + " for Char");
            }
        }

        @Override
        public Character parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Character parseLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            String value = ((StringValue) input).getValue();
            if (value.length() != 1) return null;
            return value.charAt(0);
        }
    });
}
