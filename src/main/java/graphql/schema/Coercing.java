package graphql.schema;


import graphql.PublicSpi;

@PublicSpi
public interface Coercing<I, O> {


    /**
     * Called to convert a result of a DataFetcher to a valid runtime value.
     *
     * @param input is never null
     * @return never null
     * @throws graphql.GraphQLException if value input can't be serialized
     */
    O serialize(Object input);

    /**
     * Called to resolve a input from a variable.
     * Null if not possible.
     *
     * @param input is never null
     * @return never null
     * @throws graphql.GraphQLException if value input can't be serialized
     */
    I parseValue(Object input);

    /**
     * Called to convert a AST node
     *
     * @param input is never null
     * @return A null value indicates that the literal is not valid. See {@link graphql.validation.ValidationUtil#isValidLiteralValue}
     */
    I parseLiteral(Object input);
}
