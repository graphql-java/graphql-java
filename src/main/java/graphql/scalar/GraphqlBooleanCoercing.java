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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Locale;

import static graphql.Assert.assertNotNull;
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

    @NotNull
    private Boolean serializeImpl(@NotNull Object input, @NotNull Locale locale) {
        Boolean result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    i18nMsg(locale, "Boolean.notBoolean", typeName(input))
            );
        }
        return result;
    }

    @NotNull
    private Boolean parseValueImpl(@NotNull Object input, @NotNull Locale locale) {
        Boolean result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Boolean.notBoolean", typeName(input))
            );
        }
        return result;
    }

    private static boolean parseLiteralImpl(@NotNull Object input, @NotNull Locale locale) {
        if (!(input instanceof BooleanValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Boolean.unexpectedAstType", typeName(input))
            );
        }
        return ((BooleanValue) input).isValue();
    }

    @NotNull
    private BooleanValue valueToLiteralImpl(@NotNull Object input, @NotNull Locale locale) {
        Boolean result = assertNotNull(convertImpl(input), () -> i18nMsg(locale, "Boolean.notBoolean", typeName(input)));
        return BooleanValue.newBooleanValue(result).build();
    }

    @Override
    @Deprecated
    public Boolean serialize(@NotNull Object dataFetcherResult) {
        return serializeImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Boolean serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingSerializeException {
        return serializeImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public Boolean parseValue(@NotNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Boolean parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public Boolean parseLiteral(@NotNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Boolean parseLiteral(@NotNull Value<?> input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseLiteralException {
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
