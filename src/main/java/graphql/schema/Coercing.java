package graphql.schema;


import graphql.GraphQLContext;
import graphql.PublicSpi;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import org.jspecify.annotations.NonNull;
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
 * and {@link #valueToLiteral(Object)} which converts an external input value into an AST literal.
 * <p>
 * The four methods form a consistent value conversion system:
 * <ul>
 *   <li>{@link #serialize(Object)} - converts an internal (runtime/Java) value to an external (serialized) value</li>
 *   <li>{@link #parseValue(Object)} - converts an external (serialized) value to an internal (runtime/Java) value</li>
 *   <li>{@link #parseLiteral(Object)} - converts an AST {@link Value} literal to an internal (runtime/Java) value</li>
 *   <li>{@link #valueToLiteral(Object)} - converts an external (serialized) value to an AST {@link Value} literal</li>
 * </ul>
 * <p>
 * The relationship between the input coercion methods is as follows:
 * every valid external input value for {@link #parseValue(Object)} must also be valid for
 * {@link #valueToLiteral(Object)} and vice versa. Furthermore, the AST literals returned by
 * {@link #valueToLiteral(Object)} must be valid input for {@link #parseLiteral(Object)}.
 */
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
    default @Nullable O serialize(@NonNull Object dataFetcherResult) throws CoercingSerializeException {
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
    default @Nullable O serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
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
    default @Nullable I parseValue(@NonNull Object input) throws CoercingParseValueException {
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
    default I parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
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
    default @Nullable I parseLiteral(@NonNull Object input) throws CoercingParseLiteralException {
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
    default @Nullable I parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
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
    default @Nullable I parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
        assertNotNull(input);
        assertNotNull(graphQLContext);
        assertNotNull(locale);
        return parseLiteral(input, variables.toMap());
    }


    /**
     * This is deprecated and you should implement {@link #valueToLiteral(Object, GraphQLContext, Locale)} instead
     * <p>
     * Converts an external input value to an AST {@link Value} literal. The input is an external value in the
     * same form that {@link #parseValue(Object)} accepts (e.g., a JSON-deserialized object such as a String,
     * Number, or Map), not an internal runtime value. This is the inverse direction of {@link #parseLiteral(Object)}:
     * while {@code parseLiteral} converts AST to internal, {@code valueToLiteral} converts external to AST.
     * <p>
     * This method is used in contexts such as introspection default value rendering and schema printing, where
     * an external value needs to be represented as an AST literal.
     * <p>
     * IMPORTANT: the argument is validated before by calling {@link #parseValue(Object)}, so implementations
     * can assume the input is a valid external value for this scalar type.
     *
     * @param input an external input value (same form as {@link #parseValue(Object)} input)
     *
     * @return an AST {@link Value} literal representing the input value
     */
    @Deprecated(since = "2022-08-22")
    default @NonNull Value valueToLiteral(@NonNull Object input) {
        throw new UnsupportedOperationException("The non deprecated version of valueToLiteral has not been implemented by this scalar : " + this.getClass());
    }

    /**
     * Converts an external input value to an AST {@link Value} literal. The input is an external value in the
     * same form that {@link #parseValue(Object, GraphQLContext, Locale)} accepts (e.g., a JSON-deserialized object
     * such as a String, Number, or Map), not an internal runtime value. This is the inverse direction of
     * {@link #parseLiteral(Value, CoercedVariables, GraphQLContext, Locale)}: while {@code parseLiteral} converts
     * AST to internal, {@code valueToLiteral} converts external to AST.
     * <p>
     * This method is used in contexts such as introspection default value rendering and schema printing, where
     * an external value needs to be represented as an AST literal.
     * <p>
     * IMPORTANT: the argument is validated before by calling {@link #parseValue(Object, GraphQLContext, Locale)},
     * so implementations can assume the input is a valid external value for this scalar type.
     *
     * @param input          an external input value (same form as {@link #parseValue(Object, GraphQLContext, Locale)} input)
     * @param graphQLContext the graphql context in place
     * @param locale         the locale to use
     *
     * @return an AST {@link Value} literal representing the input value
     */
    default @NonNull Value<?> valueToLiteral(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) {
        assertNotNull(input);
        assertNotNull(graphQLContext);
        assertNotNull(locale);
        return valueToLiteral(input);
    }
}
