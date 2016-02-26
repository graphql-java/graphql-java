package graphql;


import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

public class Scalars {


    public static GraphQLScalarType GraphQLInt = new GraphQLScalarType("Int", "Built-in Int", new Coercing<Integer>() {
        @Override
        public Integer serialize(Object input) {
            if (input instanceof String) {
                return Integer.parseInt((String) input);
            } else if (input instanceof Integer) {
                return (Integer) input;
            } else {
                return null;
            }
        }

        @Override
        public Integer parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Integer parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            return ((IntValue) input).getValue();
        }
    });


    public static GraphQLScalarType GraphQLLong = new GraphQLScalarType("Long", "Long type", new Coercing<Long>() {
        @Override
        public Long serialize(Object input) {
            if (input instanceof String) {
                return Long.parseLong((String) input);
            } else if (input instanceof Long) {
                return (Long) input;
            } else if (input instanceof Integer) {
                return ((Integer) input).longValue();
            } else {
                return null;
            }
        }

        @Override
        public Long parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Long parseLiteral(Object input) {
            if (input instanceof StringValue) {
                return Long.parseLong(((StringValue) input).getValue());
            } else if (input instanceof IntValue) {
                return Long.valueOf(((IntValue) input).getValue());
            }
            return null;
        }
    });

    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Coercing<Double>() {
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
            } else {
                return null;
            }
        }

        @Override
        public Double parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Double parseLiteral(Object input) {
            return ((FloatValue) input).getValue().doubleValue();
        }
    });

    public static GraphQLScalarType GraphQLString = new GraphQLScalarType("String", "Built-in String", new Coercing<String>() {
        @Override
        public String serialize(Object input) {
            return input == null ? null : input.toString();
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


    public static GraphQLScalarType GraphQLBoolean = new GraphQLScalarType("Boolean", "Built-in Boolean", new Coercing<Boolean>() {
        @Override
        public Boolean serialize(Object input) {
            if (input instanceof Boolean) {
                return (Boolean) input;
            } else if (input instanceof Integer) {
                return (Integer) input > 0;
            } else if (input instanceof String) {
                return Boolean.parseBoolean((String) input);
            } else {
                return null;
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


    public static GraphQLScalarType GraphQLID = new GraphQLScalarType("ID", "Built-in ID", new Coercing() {
        @Override
        public Object serialize(Object input) {
            if (input instanceof String) {
                return input;
            }

            return null;
        }

        @Override
        public Object parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Object parseLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            return ((StringValue) input).getValue();
        }
    });

}
