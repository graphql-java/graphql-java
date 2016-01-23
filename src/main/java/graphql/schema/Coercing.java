package graphql.schema;


public interface Coercing {


    /**
     * Called to convert a result of a DataFetcher to a valid runtime value.
     *
     *
     * @param input
     * @return  null if not possible/invalid
     */
    Object serialize(Object input);

    /**
     * Called to resolve a input from a variable.
     * Null if not possible.
     *
     * @param input
     * @return  null if not possible/invalid
     */
    Object parseValue(Object input);

    /**
     * @param input
     * @return  null if not possible/invalid
     */
    Object parseLiteral(Object input);
}
