package graphql.scalar;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

import static graphql.Assert.assertNotNull;
import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

/**
 * The deprecated methods still have implementations in case code outside graphql-java is calling them
 * but internally the call paths have been replaced.
 */
@Internal
public class GraphqlIntCoercing implements Coercing<Integer, Integer> {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);

    private Integer convertImpl(Object input) {
        if (input instanceof Integer) {
            return (Integer) input;
        } else if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                return value.intValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    @NotNull
    private Integer serialiseImpl(Object input, @NotNull Locale locale) {
        Integer result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    i18nMsg(locale, "Int.notInt", typeName(input))
            );
        }
        return result;
    }

    @NotNull
    private Integer parseValueImpl(@NotNull Object input, @NotNull Locale locale) {
        Integer result = convertImpl(input);

        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Int.notInt", typeName(input))
            );
        }

        return result;
    }

    private static int parseLiteralImpl(Object input, @NotNull Locale locale) {
        if (!(input instanceof IntValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Scalar.unexpectedAstType", "IntValue", typeName(input))
            );
        }
        BigInteger value = ((IntValue) input).getValue();
        if (value.compareTo(INT_MIN) < 0 || value.compareTo(INT_MAX) > 0) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Int.outsideRange", value.toString())
            );
        }
        return value.intValue();
    }

    private IntValue valueToLiteralImpl(Object input, @NotNull Locale locale) {
        Integer result = assertNotNull(convertImpl(input),() -> i18nMsg(locale, "Int.notInt", typeName(input)));
        return IntValue.newIntValue(BigInteger.valueOf(result)).build();
    }


    @Override
    @Deprecated
    public Integer serialize(@NotNull Object dataFetcherResult) {
        return serialiseImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Integer serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingSerializeException {
        return serialiseImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public Integer parseValue(@NotNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Integer parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public Integer parseLiteral(@NotNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Integer parseLiteral(@NotNull Value<?> input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) throws CoercingParseLiteralException {
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
