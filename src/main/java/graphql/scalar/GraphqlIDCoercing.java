package graphql.scalar;

import graphql.Internal;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigInteger;
import java.util.UUID;

import static graphql.Assert.assertNotNull;
import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlIDCoercing implements Coercing<Object, Object> {

    private String convertImpl(Object input) {
        if (input instanceof String) {
            return (String) input;
        }
        if (input instanceof Integer) {
            return String.valueOf(input);
        }
        if (input instanceof Long) {
            return String.valueOf(input);
        }
        if (input instanceof UUID) {
            return String.valueOf(input);
        }
        if (input instanceof BigInteger) {
            return String.valueOf(input);
        }
        return String.valueOf(input);

    }

    @Override
    public String serialize(Object input) {
        String result = String.valueOf(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public String parseValue(Object input) {
        String result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public String parseLiteral(Object input) {
        if (input instanceof StringValue) {
            return ((StringValue) input).getValue();
        }
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().toString();
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'IntValue' or 'StringValue' but was '" + typeName(input) + "'."
        );
    }

    @Override
    public Value valueToLiteral(Object input) {
        String result = assertNotNull(convertImpl(input));
        return StringValue.newStringValue(result).build();
    }
}
