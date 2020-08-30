package graphql.scalar;

import graphql.Internal;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import static graphql.scalar.CoercingUtil.typeName;

@Internal
public class GraphqlCharCoercing implements Coercing<Character, Character> {

    private Character convertImpl(Object input) {
        if (input instanceof String && ((String) input).length() == 1) {
            return ((String) input).charAt(0);
        } else if (input instanceof Character) {
            return (Character) input;
        } else {
            return null;
        }

    }

    @Override
    public Character serialize(Object input) {
        Character result = convertImpl(input);
        if (result == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Char' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Character parseValue(Object input) {
        Character result = convertImpl(input);
        if (result == null) {
            throw new CoercingParseValueException(
                    "Expected type 'Char' but was '" + typeName(input) + "'."
            );
        }
        return result;
    }

    @Override
    public Character parseLiteral(Object input) {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
            );
        }
        String value = ((StringValue) input).getValue();
        if (value.length() != 1) {
            throw new CoercingParseLiteralException(
                    "Empty 'StringValue' provided."
            );
        }
        return value.charAt(0);
    }
}
