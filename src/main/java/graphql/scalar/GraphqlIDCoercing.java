package graphql.scalar;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Locale;
import java.util.UUID;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.typeName;

/**
 * The deprecated methods still have implementations in case code outside graphql-java is calling them
 * but internally the call paths have been replaced.
 */
@Internal
public class GraphqlIDCoercing implements Coercing<Object, Object> {

    private String convertImpl(Object input) {
        if (input instanceof String) {
            return (String) input;
        }
        if (input instanceof Integer) {
            return String.valueOf(input);
        }
        if (input instanceof Long) {
            return String.valueOf(input);
        }
        if (input instanceof UUID) {
            return String.valueOf(input);
        }
        if (input instanceof BigInteger) {
            return String.valueOf(input);
        }
        return String.valueOf(input);

    }

    @NotNull
    private String serializeImpl(Object input, @NotNull Locale locale) {
        String result = String.valueOf(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @NotNull
    private String parseValueImpl(Object input, @NotNull Locale locale) {
        String result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "ID.notId", typeName(input))
            );
        }
        return result;
    }

    private String parseLiteralImpl(Object input, @NotNull Locale locale) {
        if (input instanceof StringValue) {
            return ((StringValue) input).getValue();
        }
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().toString();
        }
        throw new CoercingParseLiteralException(
                i18nMsg(locale, "ID.unexpectedAstType", typeName(input))
        );
    }

    @NotNull
    private StringValue valueToLiteralImpl(Object input, @NotNull Locale locale) {
        String result = convertImpl(input);
        if (result == null) {
            assertShouldNeverHappen(i18nMsg(locale, "ID.notId", typeName(input)));
        }
        return StringValue.newStringValue(result).build();
    }

    @Override
    @Deprecated
    public String serialize(@NotNull Object dataFetcherResult) {
        return serializeImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Object serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingSerializeException {
        return serializeImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public String parseValue(@NotNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Object parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public String parseLiteral(@NotNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Object parseLiteral(@NotNull Value<?> input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseLiteralException {
        return parseLiteralImpl(input, locale);
    }

    @Override
    @Deprecated
    public Value valueToLiteral(@NotNull Object input) {
        return valueToLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @NotNull Value<?> valueToLiteral(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
        return valueToLiteralImpl(input, locale);
    }
}
