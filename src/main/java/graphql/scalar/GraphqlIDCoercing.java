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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Locale;
import java.util.UUID;

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

    @NonNull
    private String serializeImpl(Object input, @NonNull Locale locale) {
        String result = String.valueOf(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @NonNull
    private String parseValueImpl(Object input, @NonNull Locale locale) {
        String result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "ID.notId", typeName(input))
            );
        }
        return result;
    }

    private String parseLiteralImpl(Object input, @NonNull Locale locale) {
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

    @NonNull
    private StringValue valueToLiteralImpl(Object input, @NonNull Locale locale) {
        String result = convertImpl(input);
        if (result == null) {
            assertShouldNeverHappen(i18nMsg(locale, "ID.notId", typeName(input)));
        }
        return StringValue.newStringValue(result).build();
    }

    @Override
    @Deprecated
    public String serialize(@NonNull Object dataFetcherResult) {
        return serializeImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Object serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
        return serializeImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public String parseValue(@NonNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Object parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public String parseLiteral(@NonNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Object parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
        return parseLiteralImpl(input, locale);
    }

    @Override
    @Deprecated
    public Value valueToLiteral(@NonNull Object input) {
        return valueToLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @NonNull Value<?> valueToLiteral(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) {
        return valueToLiteralImpl(input, locale);
    }
}
