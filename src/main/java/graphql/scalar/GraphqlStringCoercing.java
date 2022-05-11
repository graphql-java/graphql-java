package graphql.scalar;

import graphql.Internal;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;

import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlStringCoercing implements Coercing<String, String> {
    @Override
    public String serialize(Object input) {
        return input.toString();
    }

    @Override
    public String parseValue(Object input) {
        return serialize(input);
    }

    @Override
    public String parseLiteral(Object input) {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
            );
        }
        return ((StringValue) input).getValue();
    }

    @Override
    public Value valueToLiteral(Object input) {
        return StringValue.newStringValue(input.toString()).build();
    }
}
