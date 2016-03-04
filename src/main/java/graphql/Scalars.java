package graphql;


import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

public class Scalars {


    public static GraphQLScalarType GraphQLInt = new GraphQLScalarType("Int", "Built-in Int", new Coercing() {
        @Override
        public Object serialize(Object input) {
            if (input instanceof String) {
                return Integer.parseInt((String) input);
            } else if (input instanceof Integer) {
                return input;
            } else {
                return null;
            }
        }

        @Override
        public Object parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Object parseLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            return ((IntValue) input).getIntegerValue();
        }
    });


    public static GraphQLScalarType GraphQLLong = new GraphQLScalarType("Long", "Long type", new Coercing() {
        @Override
        public Object serialize(Object input) {
            if (input instanceof String) {
                return Long.parseLong((String) input);
            } else if (input instanceof Long) {
                return input;
            } else if (input instanceof Integer) {
                return ((Integer) input).longValue();
            } else {
                return null;
            }
        }

        @Override
        public Object parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Object parseLiteral(Object input) {
            if (input instanceof StringValue) {
                return Long.parseLong(((StringValue) input).getValue());
            } else if (input instanceof IntValue) {
                return ((IntValue) input).getLongValue();
            }
            return null;
        }
    });

    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Coercing() {
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
        public Object parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Object parseLiteral(Object input) {
            return ((FloatValue) input).getValue().doubleValue();
        }
    });

    public static GraphQLScalarType GraphQLString = new GraphQLScalarType("String", "Built-in String", new Coercing() {
        @Override
        public Object serialize(Object input) {
            return input == null ? null : input.toString();
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


    public static GraphQLScalarType GraphQLBoolean = new GraphQLScalarType("Boolean", "Built-in Boolean", new Coercing() {
        @Override
        public Object serialize(Object input) {
            if (input instanceof Boolean) {
                return input;
            } else if (input instanceof Integer) {
                return (Integer) input > 0;
            } else if (input instanceof String) {
                return Boolean.parseBoolean((String) input);
            } else {
                return null;
            }
        }

        @Override
        public Object parseValue(Object input) {
            return serialize(input);
        }

        @Override
        public Object parseLiteral(Object input) {
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
