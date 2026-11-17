package graphql.schema;


import graphql.GraphQLContext;
import graphql.PublicSpi;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * The Coercing interface is used by {@link graphql.schema.GraphQLScalarType}s to parse and serialize object values.
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
 * <p>
 * The three forms of an input value are:
 * <ul>
 *     <li><b>external</b>: the value as it arrives from outside graphql-java, for example a query variable after
 *     JSON decoding, such as the {@code String} {@code "2022-05-16T19:52:37Z"}</li>
 *     <li><b>internal</b>: the Java object your data fetchers work with, such as a {@code java.time.ZonedDateTime}</li>
 *     <li><b>literal</b>: the AST {@link Value} as written in a query or SDL document, such as
 *     {@code StringValue("2022-05-16T19:52:37Z")}</li>
 * </ul>
 * The relationship between these three methods is as follows:
 * It is required that every valid external input values for {@link #parseValue(Object)} is also valid for
 * {@link #valueToLiteral(Object)}
 * and vice versa.
 * Furthermore the literals returned by {@link #valueToLiteral(Object)} are required to be valid for
 * {@link #parseLiteral(Object)}.
 * <p>
 * Note that {@link #valueToLiteral(Object, GraphQLContext, Locale)} receives an <b>external</b> value, the same kind
 * of value that {@link #parseValue(Object, GraphQLContext, Locale)} receives, and not an internal value or the result
 * of {@link #serialize(Object, GraphQLContext, Locale)}. See that method for when it is called.
 */
@NullMarked
@PublicSpi
public interface Coercing<I, O> {

    /**
     * This is deprecated and you should implement {@link #serialize(Object, GraphQLContext, Locale)} instead
     * <p>
     * Called to convert a Java object result of a DataFetcher to a valid runtime value for the scalar type.
     * <p>
     * Note : Throw {@link graphql.schema.CoercingSerializeException} if there is fundamental
     * problem during serialization, don't return null to indicate failure.
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
    @Deprecated(since = "2022-08-22")
    default @Nullable O serialize(Object dataFetcherResult) throws CoercingSerializeException {
        throw new UnsupportedOperationException("The non deprecated version of serialize has not been implemented by this scalar : " + this.getClass());
    }

    /**
     * Called to convert a Java object result of a DataFetcher to a valid runtime value for the scalar type.
     * <p>
     * Note : Throw {@link graphql.schema.CoercingSerializeException} if there is fundamental
     * problem during serialization, don't return null to indicate failure.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your serialize method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingSerializeException} instead as per the method contract.
     *
     * @param dataFetcherResult is never null
     * @param graphQLContext    the graphql context in place
     * @param locale            the locale to use
     *
     * @return a serialized value which may be null.
     *
     * @throws graphql.schema.CoercingSerializeException if value input can't be serialized
     */
    default @Nullable O serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        assertNotNull(dataFetcherResult);
        assertNotNull(graphQLContext);
        return serialize(dataFetcherResult);
    }

    /**
     * This is deprecated and you should implement {@link #parseValue(Object, GraphQLContext, Locale)} instead
     * <p>
     * Called to resolve an input from a query variable into a Java object acceptable for the scalar type.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseValue method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseValueException} instead as per the method contract.
     * <p>
     * Note : if input is explicit/raw value null, input coercion will return null before this method is called
     *
     * @param input is never null
     *
     * @return a parsed value which may be null
     *
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    @Deprecated(since = "2022-08-22")
    default @Nullable I parseValue(Object input) throws CoercingParseValueException {
        throw new UnsupportedOperationException("The non deprecated version of parseValue has not been implemented by this scalar : " + this.getClass());
    }

    /**
     * Called to resolve an input from a query variable into a Java object acceptable for the scalar type.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseValue method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseValueException} instead as per the method contract.
     *
     * Note : if input is explicit/raw value null, input coercion will return null before this method is called
     *
     * @param input          is never null
     * @param graphQLContext the graphql context in place
     * @param locale         the locale to use
     *
     * @return a parsed value which may be null
     *
     * @throws graphql.schema.CoercingParseValueException if value input can't be parsed
     */
    @Nullable
    default I parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        assertNotNull(input);
        assertNotNull(graphQLContext);
        assertNotNull(locale);
        return parseValue(input);
    }

    /**
     * This is deprecated and you should implement {@link #parseLiteral(Value, CoercedVariables, GraphQLContext, Locale)} instead
     * <p>
     * Called during query validation to convert a query input AST node into a Java object acceptable for the scalar type.  The input
     * object will be an instance of {@link graphql.language.Value}.
     * <p>
     * Note : You should not allow {@link java.lang.RuntimeException}s to come out of your parseLiteral method, but rather
     * catch them and fire them as {@link graphql.schema.CoercingParseLiteralException} instead as per the method contract.
     * <p>
     * Note : if input is literal {@link graphql.language.NullValue}, input coercion will return null before this method is called
     *
     * @param input is never null
     *
     * @return a parsed value which may be null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    @Deprecated(since = "2022-08-22")
    default @Nullable I parseLiteral(Object input) throws CoercingParseLiteralException {
        throw new UnsupportedOperationException("The non deprecated version of parseLiteral has not been implemented by this scalar : " + this.getClass());
    }

    /**
     * This is deprecated and you should implement {@link #parseLiteral(Value, CoercedVariables, GraphQLContext, Locale)} instead
     * <p>
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
     * Note : if input is literal {@link graphql.language.NullValue}, input coercion will return null before this method is called
     *
     * @param input     is never null
     * @param variables the resolved variables passed to the query
     *
     * @return a parsed value which may be null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    @SuppressWarnings("unused")
    @Deprecated(since = "2022-08-22")
    default @Nullable I parseLiteral(Object input, Map<String, @Nullable Object> variables) throws CoercingParseLiteralException {
        return parseLiteral(input);
    }

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
     * Note : if input is literal {@link graphql.language.NullValue}, input coercion will return null before this method is called
     *
     * @param input          is never null
     * @param variables      the resolved variables passed to the query
     * @param graphQLContext the graphql context in place
     * @param locale         the locale to use
     *
     * @return a parsed value which may be null
     *
     * @throws graphql.schema.CoercingParseLiteralException if input literal can't be parsed
     */
    default @Nullable I parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        assertNotNull(input);
        assertNotNull(graphQLContext);
        assertNotNull(locale);
        return parseLiteral(input, variables.toMap());
    }


    /**
     * This is deprecated and you should implement {@link #valueToLiteral(Object, GraphQLContext, Locale)} instead
     * <p>
     * Converts an external input value to a literal (Ast Value).
     * <p>
     * IMPORTANT: the argument is validated before by calling {@link #parseValue(Object)}.
     *
     * @param input an external input value
     *
     * @return The literal matching the external input value.
     */
    @Deprecated(since = "2022-08-22")
    default Value valueToLiteral(Object input) {
        throw new UnsupportedOperationException("The non deprecated version of valueToLiteral has not been implemented by this scalar : " + this.getClass());
    }

    /**
     * Converts an external input value to a literal (Ast Value).
     * <p>
     * The {@code input} is an <b>external</b> value, the same kind of value that
     * {@link #parseValue(Object, GraphQLContext, Locale)} accepts (for example a {@code String} for a date time scalar).
     * It is not an internal value, so you should not call {@link #serialize(Object, GraphQLContext, Locale)} on it.
     * The returned literal must be accepted by {@link #parseLiteral(Value, CoercedVariables, GraphQLContext, Locale)}.
     * <p>
     * The normal execution of a query does not call this method. graphql-java calls it when it needs to turn an
     * external value into a literal, for example:
     * <ul>
     *     <li>when a schema element holds an external value, such as an argument or input field default value set via
     *     {@code defaultValueProgrammatic(Object)} or an applied directive argument set via
     *     {@code valueProgrammatic(Object)}, and that value is printed by {@link graphql.schema.idl.SchemaPrinter},
     *     returned by introspection or checked during validation</li>
     *     <li>when normalized variables are computed from the query variables, see
     *     {@link graphql.execution.ValuesResolver#getNormalizedVariableValues}</li>
     * </ul>
     * Values set via {@code defaultValueLiteral(Value)} are already literals, and values set via the deprecated
     * {@code defaultValue(Object)} are internal values that are converted with
     * {@link #serialize(Object, GraphQLContext, Locale)} instead. If your schema only uses those, this method is never
     * called and the default implementation, which throws {@link UnsupportedOperationException}, does no harm.
     * Implement it if your scalar can appear in any of the cases above.
     * <p>
     * IMPORTANT: the argument is validated before by calling {@link #parseValue(Object, GraphQLContext, Locale)}.
     *
     * @param input          an external input value
     * @param graphQLContext the graphql context in place
     * @param locale         the locale to use
     *
     * @return The literal matching the external input value.
     */
    default Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        assertNotNull(input);
        assertNotNull(graphQLContext);
        assertNotNull(locale);
        return valueToLiteral(input);
    }
}
