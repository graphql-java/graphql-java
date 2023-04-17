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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    private String parseLiteralImpl(@NotNull Object input, Locale locale) {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Scalar.unexpectedAstType", "StringValue", typeName(input))
            );
        }
        return ((StringValue) input).getValue();
    }

    private StringValue valueToLiteralImpl(@NotNull Object input) {
        return StringValue.newStringValue(input.toString()).build();
    }

    @Override
    @Deprecated
    public String serialize(@NotNull Object dataFetcherResult) {
        return toStringImpl(dataFetcherResult);
    }

    @Override
    public @Nullable String serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingSerializeException {
        return toStringImpl(dataFetcherResult);
    }

    @Override
    @Deprecated
    public String parseValue(@NotNull Object input) {
        return toStringImpl(input);
    }

    @Override
    public String parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseValueException {
        return toStringImpl(input);
    }

    @Override
    @Deprecated
    public String parseLiteral(@NotNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable String parseLiteral(@NotNull Value<?> input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseLiteralException {
        return parseLiteralImpl(input, locale);
    }

    @Override
    @Deprecated
    public Value valueToLiteral(@NotNull Object input) {
        return valueToLiteralImpl(input);
    }

    @Override
    public @NotNull Value<?> valueToLiteral(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
        return valueToLiteralImpl(input);
    }
}
