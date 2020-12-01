package graphql.scalar;

import graphql.Internal;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlIDCoercing implements Coercing<Object, Object> {

    private String convertImpl(Object input) {
        return (input == null) ? "" : input.toString();
    }

    @Override
    public String serialize(Object input) {
        if (input instanceof Boolean) {
            throw new CoercingSerializeException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return convertImpl(input);
    }

    @Override
    public String parseValue(Object input) {
        if (input instanceof Boolean) {
            throw new CoercingParseValueException(
                    "Expected type 'ID' but was '" + typeName(input) + "'."
            );
        }
        return convertImpl(input);
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
}
