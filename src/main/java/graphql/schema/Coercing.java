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
     * @param dataFetcherResult is never null
     *
     * @return never null
     *
     * @throws graphql.schema.CoercingSerializeException if value input can't be serialized
     */
    O serialize(Object dataFetcherResult);

    /**
     * Called to resolve a input from a query variable into a Java object acceptable for the scalar type.
     *
     * @param input is never null
     *
     * @return never null
     *
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    I parseValue(Object input);

    /**
     * Called to convert an query input AST node into a Java object acceptable for the scalar type.  The input
     * object will be an instance of {@link graphql.language.Value}.
     *
     * @param input is never null
     *
     * @return A null value indicates that the literal is not valid. See {@link graphql.validation.ValidationUtil#isValidLiteralValue}
     */
    I parseLiteral(Object input);
}
