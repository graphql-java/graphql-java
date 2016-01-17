package graphql.schema;


public interface Coercing {


    Object coerce(Object input);

    Object coerceValue(Object input);

    /**
     * @param input
     * @return return null if not valid
     */
    Object parseLiteral(Object input);
}
