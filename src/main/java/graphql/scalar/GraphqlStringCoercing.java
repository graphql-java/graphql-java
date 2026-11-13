package graphql.scalar;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.typeName;

/**
 * The deprecated methods still have implementations in case code outside graphql-java is calling them
 * but internally the call paths have been replaced.
 */
@Internal
public class GraphqlStringCoercing implements Coercing<String, String> {

    private String toStringImpl(Object input) {
        return String.valueOf(input);
    }

    private String parseValueImpl(@NonNull Object input, @NonNull Locale locale) {
        if (!(input instanceof String)) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "String.unexpectedRawValueType", typeName(input))
            );
        }
        return (String) input;
    }

    private String parseLiteralImpl(@NonNull Object input, Locale locale) {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Scalar.unexpectedAstType", "StringValue", typeName(input))
            );
        }
        return ((StringValue) input).getValue();
    }

    private StringValue valueToLiteralImpl(@NonNull Object input) {
        return StringValue.newStringValue(input.toString()).build();
    }

    @Override
    @Deprecated
    public String serialize(@NonNull Object dataFetcherResult) {
        return toStringImpl(dataFetcherResult);
    }

    @Override
    public @Nullable String serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
        return toStringImpl(dataFetcherResult);
    }

    @Override
    @Deprecated
    public String parseValue(@NonNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public String parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public String parseLiteral(@NonNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable String parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
        return parseLiteralImpl(input, locale);
    }

    @Override
    @Deprecated
    public @NonNull Value<?> valueToLiteral(@NonNull Object input) {
        return valueToLiteralImpl(input);
    }

    @Override
    public @NonNull Value<?> valueToLiteral(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) {
        return valueToLiteralImpl(input);
    }
}
