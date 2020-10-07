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

import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlBigDecimalCoercing implements Coercing<BigDecimal, BigDecimal> {

    private BigDecimal convertImpl(Object input) {
        if (isNumberIsh(input)) {
            try {
                return new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;

    }

    @Override
    public BigDecimal serialize(Object input) {
        BigDecimal result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'BigDecimal' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public BigDecimal parseValue(Object input) {
        BigDecimal result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'BigDecimal' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public BigDecimal parseLiteral(Object input) {
        if (input instanceof StringValue) {
            try {
                return new BigDecimal(((StringValue) input).getValue());
            } catch (NumberFormatException e) {
                throw new CoercingParseLiteralException(
                        "Unable to turn AST input into a 'BigDecimal' : '" + input + "'"
                );
            }
        } else if (input instanceof IntValue) {
            return new BigDecimal(((IntValue) input).getValue());
        } else if (input instanceof FloatValue) {
            return ((FloatValue) input).getValue();
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'IntValue', 'StringValue' or 'FloatValue' but was '" + typeName(input) + "'."
        );
    }
}
