package graphql;


import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

public class Scalars {

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

}
