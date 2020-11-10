package graphql.scalar;

import graphql.Internal;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlBigIntegerCoercing implements Coercing<BigInteger, BigInteger> {

    private BigInteger convertImpl(Object input) {
        if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                return value.toBigIntegerExact();
            } catch (ArithmeticException e) {
                return null;
            }
        }
        return null;

    }

    @Override
    public BigInteger serialize(Object input) {
        BigInteger result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'BigInteger' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public BigInteger parseValue(Object input) {
        BigInteger result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'BigInteger' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public BigInteger parseLiteral(Object input) {
        if (input instanceof StringValue) {
            try {
                return new BigDecimal(((StringValue) input).getValue()).toBigIntegerExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new CoercingParseLiteralException(
                        "Unable to turn AST input into a 'BigInteger' : '" + input + "'"
                );
            }
        } else if (input instanceof IntValue) {
            return ((IntValue) input).getValue();
        } else if (input instanceof FloatValue) {
            try {
                return ((FloatValue) input).getValue().toBigIntegerExact();
            } catch (ArithmeticException e) {
                throw new CoercingParseLiteralException(
                        "Unable to turn AST input into a 'BigInteger' : '" + input + "'"
                );
            }
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'IntValue', 'StringValue' or 'FloatValue' but was '" + typeName(input) + "'."
        );
    }
}
