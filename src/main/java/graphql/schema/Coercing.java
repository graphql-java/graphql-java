package graphql.schema;


import graphql.PublicSpi;
import graphql.language.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The Coercing interface is used by {@link graphql.schema.GraphQLScalarType}s to parse and serialise object values.
 * <p>
 * There are two major responsibilities, result coercion and input coercion.
 * <p>
 * Result coercion is taking a value from a Java object and coercing it into the constraints of the scalar type.
 * For example imagine a DateTime scalar, the result coercion would need to take an object and turn it into a
 * ISO date or throw an exception if it cant.
 * <p>
 * Input coercion is made out of three different methods {@link #parseLiteral(Object)} which converts an literal Ast
 * into an internal input value, {@link #parseValue(Object)} which converts an external input value into an internal one
 * and {@link #valueToLiteral(Object)} which is a translation between an external input value into a literal.
 * <br>
 * The relationship between these three methods is as follows:
 * It is required that every valid external input values for {@link #parseValue(Object)} is also valid for
 * {@link #valueToLiteral(Object)}
 * and vice versa.
 * Furthermore the literals returned by {@link #valueToLiteral(Object)} are required to be valid for
 * {@link #parseLiteral(Object)}.
 */
@PublicSpi
public interface Coercing<I, O> {

    /**
     * Called to convert a Java object result of a DataFetcher to a valid runtime value for the scalar type.
     * <p>
     * Note : Throw {@link graphql.schema.CoercingSerializeException} if there is fundamental
     * problem during serialisation, don't return null to indicate failure.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your serialize method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingSerializeException} instead as per the method contract.
     *
     * @param dataFetcherResult is never null
     *
     * @return a serialized value which may be null.
     *
     * @throws graphql.schema.CoercingSerializeException if value input can't be serialized
     */
    O serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException;

    /**
     * Called to resolve an input from a query variable into a Java object acceptable for the scalar type.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseValue method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseValueException} instead as per the method contract.
     *
     * @param input is never null
     *
     * @return a parsed value which is never null
     *
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    @NotNull I parseValue(@NotNull Object input) throws CoercingParseValueException;

    /**
     * Called during query validation to convert a query input AST node into a Java object acceptable for the scalar type.  The input
     * object will be an instance of {@link graphql.language.Value}.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseLiteral method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseLiteralException} instead as per the method contract.
     *
     * @param input is never null
     *
     * @return a parsed value which is never null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    @NotNull I parseLiteral(@NotNull Object input) throws CoercingParseLiteralException;

    /**
     * Called during query execution to convert a query input AST node into a Java object acceptable for the scalar type.  The input
     * object will be an instance of {@link graphql.language.Value}.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseLiteral method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseLiteralException} instead as per the method contract.
     * <p>
     * Many scalar types don't need to implement this method because they don't take AST {@link graphql.language.VariableReference}
     * objects and convert them into actual values.  But for those scalar types that want to do this, then this
     * method should be implemented.
     *
     * @param input     is never null
     * @param variables the resolved variables passed to the query
     *
     * @return a parsed value which is never null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    @SuppressWarnings("unused")
    default @NotNull I parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
        return parseLiteral(input);
    }


    /**
     * Converts an external input value to a literal (Ast Value).
     *
     * IMPORTANT: the argument is validated before by calling {@link #parseValue(Object)}.
     *
     * @param input an external input value
     *
     * @return The literal matching the external input value.
     */
    default @NotNull Value valueToLiteral(@NotNull Object input) {
        throw new UnsupportedOperationException("This is not implemented by this Scalar " + this.getClass());
    }
}
