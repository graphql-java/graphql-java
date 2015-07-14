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
        public Object coerce(Object input) {
            if (input instanceof String) {
                return Integer.parseInt((String) input);
            } else if (input instanceof Integer) {
                return input;
            } else {
                throw new GraphQLException("");
            }
        }

        @Override
        public Object coerceLiteral(Object input) {
            if (!(input instanceof IntValue)) return null;
            return ((IntValue) input).getValue();
        }
    });

    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Coercing() {
        @Override
        public Object coerce(Object input) {
            if (input instanceof String) {
                return Float.parseFloat((String) input);
            } else if (input instanceof Float) {
                return input;
            } else {
                throw new GraphQLException();
            }
        }

        @Override
        public Object coerceLiteral(Object input) {
            return ((FloatValue) input).getValue().floatValue();
        }
    });

    public static GraphQLScalarType GraphQLString = new GraphQLScalarType("String", "Built-in String", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input.toString();
        }

        @Override
        public Object coerceLiteral(Object input) {
            if (!(input instanceof StringValue)) return null;
            return ((StringValue) input).getValue();
        }
    });


    public static GraphQLScalarType GraphQLBoolean = new GraphQLScalarType("Boolean", "Built-in Boolean", new Coercing() {
        @Override
        public Object coerce(Object input) {
            if (input instanceof Boolean) {
                return input;
            } else if (input instanceof Integer) {
                return (Integer) input > 0;
            } else if (input instanceof String) {
                return Boolean.parseBoolean((String) input);
            } else {
                throw new GraphQLException();
            }
        }

        @Override
        public Object coerceLiteral(Object input) {
            if (!(input instanceof BooleanValue)) return null;
            return ((BooleanValue) input).isValue();
        }
    });


//    public static GraphQLScalarType GraphQLID = new GraphQLScalarType("ID", "Built-in ID", new Coercing() {
//        @Override
//        public Object coerce(Object input) {
//            return input;
//        }
//
//        @Override
//        public Object coerceLiteral(Object input) {
//            return input;
//        }
//    });

}
