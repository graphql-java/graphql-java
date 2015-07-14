package graphql.schema;


public interface Coercing {


    Object coerce(Object input);

    /**
     * @param input
     * @return return null if not valid
     */
    Object coerceLiteral(Object input);
}
