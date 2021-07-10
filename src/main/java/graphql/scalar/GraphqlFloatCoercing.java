package graphql.scalar;

import graphql.Internal;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;

import static graphql.Assert.assertNotNull;
import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlFloatCoercing implements Coercing<Double, Double> {

    private Double convertImpl(Object input) {
        if (isNumberIsh(input)) {
            BigDecimal value;
            try {
                value = new BigDecimal(input.toString());
            } catch (NumberFormatException e) {
                return null;
            }
            return value.doubleValue();
        } else {
            return null;
        }

    }

    @Override
    public Double serialize(Object input) {
        Double result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Float' but was '" + typeName(input) + "'."
            );
        }
        return result;

    }

    @Override
    public Double parseValue(Object input) {
        Double result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'Float' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Double parseLiteral(Object input) {
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().doubleValue();
        } else if (input instanceof FloatValue) {
            return ((FloatValue) input).getValue().doubleValue();
        } else {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'IntValue' or 'FloatValue' but was '" + typeName(input) + "'."
            );
        }
    }

    @Override
    public Value valueToLiteral(Object input) {
        Double result = assertNotNull(convertImpl(input));
        return FloatValue.newFloatValue(BigDecimal.valueOf(result)).build();
    }
}

