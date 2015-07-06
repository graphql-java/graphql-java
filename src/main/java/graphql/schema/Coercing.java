package graphql.schema;


public interface Coercing {

    Object coerce(Object input);

    Object coerceLiteral(Object input);
}
