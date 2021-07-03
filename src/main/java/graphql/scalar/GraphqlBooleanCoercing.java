package graphql.scalar;

import graphql.Internal;
import graphql.language.BooleanValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.scalar.CoercingUtil.isNumberIsh;
import static graphql.scalar.CoercingUtil.typeName;

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

    @Override
    public Boolean serialize(Object input) {
        Boolean result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Boolean' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Boolean parseValue(Object input) {
        Boolean result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'Boolean' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Boolean parseLiteral(Object input) {
        if (!(input instanceof BooleanValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'BooleanValue' but was '" + typeName(input) + "'."
            );
        }
        return ((BooleanValue) input).isValue();
    }

    @Override
    public Value valueToLiteral(Object input) {
        Boolean result = assertNotNull(convertImpl(input));
        return BooleanValue.newBooleanValue(result).build();
    }
}
