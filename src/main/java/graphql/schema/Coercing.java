package graphql.schema;


public interface Coercing<I, O> {


    /**
     * Called to convert a result of a DataFetcher to a valid runtime value.
     *
     * @param input is never null
     * @return null if not possible/invalid
     */
    O serialize(Object input);

    /**
     * Called to resolve a input from a variable.
     * Null if not possible.
     *
     * @param input is never null
     * @return null if not possible/invalid
     */
    I parseValue(Object input);

    /**
     * Called to convert a AST node
     *
     * @param input is never null
     * @return null if not possible/invalid
     */
    I parseLiteral(Object input);
}
