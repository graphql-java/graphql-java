package graphql.schema;


import graphql.PublicSpi;

@PublicSpi
public interface Coercing<I, O> {


    /**
     * Called to convert a result of a DataFetcher to a valid runtime value.
     *
     * @param dataFetcherResult is never null
     * @return never null
     * @throws graphql.schema.CoercingSerializeException if value input can't be serialized
     */
    O serialize(Object dataFetcherResult);

    /**
     * Called to resolve a input from a variable.
     *
     * @param input is never null
     * @return never null
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    I parseValue(Object input);

    /**
     * Called to convert an input AST node
     *
     * @param input is never null
     * @return A null value indicates that the literal is not valid. See {@link graphql.validation.ValidationUtil#isValidLiteralValue}
     */
    I parseLiteral(Object input);
}
