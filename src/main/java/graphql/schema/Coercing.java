package graphql.schema;


public interface Coercing {


    /**
     * Called to convert a result of a DataFetcher to a valid runtime value.
     * @param input
     * @return
     */
    Object serialize(Object input);

    /**
     * Called to resolve a input from a variable.
     *
     * @param input
     * @return
     */
    Object parseValue(Object input);

    /**
     * @param input
     * @return return null if not valid
     */
    Object parseLiteral(Object input);
}
