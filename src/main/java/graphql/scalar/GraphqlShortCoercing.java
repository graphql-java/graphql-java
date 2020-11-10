package graphql.scalar;

import graphql.Internal;
import graphql.language.IntValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlShortCoercing implements Coercing<Short, Short> {

    private static final BigInteger SHORT_MAX = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger SHORT_MIN = BigInteger.valueOf(Short.MIN_VALUE);

    private Short convertImpl(Object input) {
        if (input instanceof Short) {
            return (Short) input;
        } else if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                return value.shortValueExact();
            } catch (ArithmeticException e) {
                return null;
            }
        } else {
            return null;
        }

    }

    @Override
    public Short serialize(Object input) {
        Short result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Short' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Short parseValue(Object input) {
        Short result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'Short' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Short parseLiteral(Object input) {
        if (!(input instanceof IntValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'IntValue' but was '" + typeName(input) + "'."
            );
        }
        BigInteger value = ((IntValue) input).getValue();
        if (value.compareTo(SHORT_MIN) < 0 || value.compareTo(SHORT_MAX) > 0) {
            throw new CoercingParseLiteralException(
                    "Expected value to be in the Short range but it was '" + value.toString() + "'"
            );
        }
        return value.shortValue();
    }
}
