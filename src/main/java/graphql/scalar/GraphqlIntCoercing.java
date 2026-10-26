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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
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
public class GraphqlIntCoercing implements Coercing<Integer, Integer> {

    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);

    // Integer.MAX_VALUE / Integer.MIN_VALUE both have 10 significant digits
    private static final int INT_MAX_DIGITS = 10;

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

    @NonNull
    private Integer serialiseImpl(Object input, @NonNull Locale locale) {
        Integer result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    i18nMsg(locale, "Int.notInt", typeName(input))
            );
        }
        return result;
    }

    @NonNull
    private Integer parseValueImpl(@NonNull Object input, @NonNull Locale locale) {
        if (!(input instanceof Number)) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Int.notInt", typeName(input))
            );
        }

        if (input instanceof Integer) {
            return (Integer) input;
        }

        BigInteger result = convertParseValueImpl(input, locale);
        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Int.notInt", typeName(input))
            );
        }
        if (result.compareTo(INT_MIN) < 0 || result.compareTo(INT_MAX) > 0) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Int.outsideRange", result.toString())
            );
        }
        return result.intValueExact();
    }

    private BigInteger convertParseValueImpl(Object input, @NonNull Locale locale) {
        BigDecimal value;
        try {
            value = new BigDecimal(input.toString());
        } catch (NumberFormatException e) {
            return null;
        }

        // A value whose integer part has more digits than Integer can hold is out of range by
        // definition. Decide that from the decimal's scale/precision instead of letting
        // toBigIntegerExact() expand it: a variable value like 1E1000000000 arrives here as a
        // BigDecimal and would otherwise materialise a BigInteger with ~10^9 digits (CPU/heap blow
        // up) before the range check in parseValueImpl ever runs. Use long math because scale can
        // be close to Integer.MIN_VALUE.
        if ((long) value.precision() - value.scale() > INT_MAX_DIGITS) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Int.outsideRange", value.toString())
            );
        }

        try {
            return value.toBigIntegerExact();
        } catch (ArithmeticException e) {
            // Exception if number has non-zero fractional part
            return null;
        }
    }

    private static int parseLiteralImpl(Object input, @NonNull Locale locale) {
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

    private IntValue valueToLiteralImpl(Object input, @NonNull Locale locale) {
        Integer result = convertImpl(input);
        if (result == null) {
            assertShouldNeverHappen(i18nMsg(locale, "Int.notInt", typeName(input)));
        }
        return IntValue.newIntValue(BigInteger.valueOf(result)).build();
    }


    @Override
    @Deprecated
    public Integer serialize(@NonNull Object dataFetcherResult) {
        return serialiseImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Integer serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
        return serialiseImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public Integer parseValue(@NonNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Integer parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public Integer parseLiteral(@NonNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Integer parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
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
