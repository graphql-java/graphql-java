package graphql.scalar;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.IntValue;
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
public class GraphqlFloatCoercing implements Coercing<Double, Double> {

    private Double convertImpl(Object input) {
        // From the GraphQL Float spec, non-finite floating-point internal values (NaN and Infinity)
        // must raise a field error on both result and input coercion
        Double doubleInput;
        if (input instanceof Double) {
            doubleInput = (Double) input;
        } else if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
            doubleInput = value.doubleValue();
        } else {
            return null;
        }

        if (Double.isNaN(doubleInput) || Double.isInfinite(doubleInput)) {
            return null;
        }
        return doubleInput;
    }

    @NonNull
    private Double serialiseImpl(Object input, @NonNull Locale locale) {
        Double result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    i18nMsg(locale, "Float.notFloat", typeName(input))
            );
        }
        return result;
    }

    @NonNull
    private Double parseValueImpl(@NonNull Object input, @NonNull Locale locale) {
        if (!(input instanceof Number)) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Float.unexpectedRawValueType", typeName(input))
            );
        }

        Double result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    i18nMsg(locale, "Float.notFloat", typeName(input))
            );
        }

        return result;
    }

    private static double parseLiteralImpl(@NonNull Object input, @NonNull Locale locale) {
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().doubleValue();
        } else if (input instanceof FloatValue) {
            return ((FloatValue) input).getValue().doubleValue();
        } else {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Float.unexpectedAstType", typeName(input))
            );
        }
    }

    @NonNull
    private FloatValue valueToLiteralImpl(Object input, @NonNull Locale locale) {
        Double result = convertImpl(input);
        if (result == null) {
            assertShouldNeverHappen(i18nMsg(locale, "Float.notFloat", typeName(input)));
        }
        return FloatValue.newFloatValue(BigDecimal.valueOf(result)).build();
    }

    @Override
    @Deprecated
    public Double serialize(@NonNull Object dataFetcherResult) {
        return serialiseImpl(dataFetcherResult, Locale.getDefault());
    }

    @Override
    public @Nullable Double serialize(@NonNull Object dataFetcherResult, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException {
        return serialiseImpl(dataFetcherResult, locale);
    }

    @Override
    @Deprecated
    public @NonNull Double parseValue(@NonNull Object input) {
        return parseValueImpl(input, Locale.getDefault());
    }

    @Override
    public Double parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException {
        return parseValueImpl(input, locale);
    }

    @Override
    @Deprecated
    public Double parseLiteral(@NonNull Object input) {
        return parseLiteralImpl(input, Locale.getDefault());
    }

    @Override
    public @Nullable Double parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException {
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

