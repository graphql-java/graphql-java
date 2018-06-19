package graphql.schema;


import graphql.PublicSpi;

/**
 * The Coercing interface is used by {@link graphql.schema.GraphQLScalarType}s to parse and serialise object values.
 * <p>
 * There are two major responsibilities, result coercion and input coercion.
 * <p>
 * Result coercion is taking a value from a Java object and coercing it into the constraints of the scalar type.
 * For example imagine a DateTime scalar, the result coercion would need to take an object and turn it into a
 * ISO date or throw an exception if it cant.
 * <p>
 * Input coercion is taking a value that came in from requests variables or hard coded query literals and coercing them into a
 * Java object value that is acceptable to the scalar type.  Again using the DateTime example, the input coercion would try to
 * parse an ISO date time object or throw an exception if it cant.
 *
 * See http://facebook.github.io/graphql/#sec-Scalars
 */
@PublicSpi
public interface Coercing<I, O> {

    /**
     * Called to convert a Java object result of a DataFetcher to a valid runtime value for the scalar type.
     *
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your serialize method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingSerializeException} instead as per the method contract.
     *
     * @param dataFetcherResult is never null
     *
     * @return a serialized value which is never null
     *
     * @throws graphql.schema.CoercingSerializeException if value input can't be serialized
     */
    O serialize(Object dataFetcherResult) throws CoercingSerializeException;

    /**
     * Called to resolve a input from a query variable into a Java object acceptable for the scalar type.
     *
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseValue method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingSerializeException} instead as per the method contract.
     *
     * @param input is never null
     *
     * @return a parsed value which is never null
     *
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    I parseValue(Object input) throws CoercingParseValueException;

    /**
     * Called to convert an query input AST node into a Java object acceptable for the scalar type.  The input
     * object will be an instance of {@link graphql.language.Value}.
     *
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseLiteral method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseLiteralException} instead as per the method contract.
     *
     * @param input is never null
     *
     * @return a parsed value which is never null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    I parseLiteral(Object input) throws CoercingParseLiteralException;
}
