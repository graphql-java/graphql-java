package graphql;


import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

public class Scalars {


    public static GraphQLScalarType GraphQLInt = new GraphQLScalarType("Int", "Built-in Int", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input;
        }

        @Override
        public Object coerceLiteral(Object input) {
            return input;
        }
    });

    public static GraphQLScalarType GraphQLFloat = new GraphQLScalarType("Float", "Built-in Float", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input;
        }

        @Override
        public Object coerceLiteral(Object input) {
            return input;
        }
    });

    public static GraphQLScalarType GraphQLString = new GraphQLScalarType("String", "Built-in String", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input;
        }

        @Override
        public Object coerceLiteral(Object input) {
            return input;
        }
    });


    public static GraphQLScalarType GraphQLBoolean = new GraphQLScalarType("Boolean", "Built-in Boolean", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input;
        }

        @Override
        public Object coerceLiteral(Object input) {
            return input;
        }
    });


    public static GraphQLScalarType GraphQLID = new GraphQLScalarType("ID", "Built-in ID", new Coercing() {
        @Override
        public Object coerce(Object input) {
            return input;
        }

        @Override
        public Object coerceLiteral(Object input) {
            return input;
        }
    });

}
