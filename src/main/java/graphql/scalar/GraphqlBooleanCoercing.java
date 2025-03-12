package graphql.scalar;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.BooleanValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

/**
 * The deprecated methods still have implementations in case code outside graphql-java is calling them
 * but internally the call paths have been replaced.
 */
@Internal
public class GraphqlBooleanCoercing implements Coercing<Boolean, Boolean> {

    private Boolean convertImpl(Object input) {
        if (input instanceof Boolean) {
            return (Boolean) input;
        } else if (input instanceof String) {
            String lStr = ((String) input).toLowerCase();
            if (lStr.equals("true")) {
                return true;
            }
            if (lStr.equals("false")) {
                return false;
            }
            return null;
        } else if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                // this should never happen because String is handled above
                return assertShouldNeverHappen();
            }
            return value.compareTo(BigDecimal.ZERO) != 0;
        } else {
            return null;
        }

    }

    @NonNull
    private Boolean serializeImpl(@NonNull Object input, @NonNull Locale locale) {
        Boolean result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    i18nMsg(locale, "Boolean.notBoolean", typeName(input))
            );
        }
        return result;
    }

    @NonNull
    private Boolean parseValueImpl(@NonNull Object input, @NonNull Locale locale) {
        if (!(input instanceof Boolean)) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Boolean.unexpectedRawValueType", typeName(input))
            );
        }
        return (Boolean) input;
    }

    private static boolean parseLiteralImpl(@NonNull Object input, @NonNull Locale locale) {
        if (!(input instanceof BooleanValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Boolean.unexpectedAstType", typeName(input))
            );
        }
        return ((BooleanValue) input).isValue();
    }

    @NonNull
    private BooleanValue valueToLiteralImpl(@NonNull Object input, @NonNull Locale locale) {
        Boolean result = convertImpl(input);
        if (result == null) {
            assertShouldNeverHappen(i18nMsg(locale, "Boolean.notBoolean", typeName(input)));
        }
        return BooleanValue.newBooleanValue(result).build();
    }

    @Override
    @Deprecated
    public Boolean serialize(@NonNull Object dataFetcherResult) {
        return serializeImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Boolean serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
        return serializeImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public Boolean parseValue(@NonNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Boolean parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public Boolean parseLiteral(@NonNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Boolean parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
        return parseLiteralImpl(input, locale);
    }

    @Override
    @Deprecated
    public @NonNull Value<?> valueToLiteral(@NonNull Object input) {
        return valueToLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @NonNull Value<?> valueToLiteral(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) {
        return valueToLiteralImpl(input, locale);
    }
}
