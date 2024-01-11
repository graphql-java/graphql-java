package readme;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class ScalarExamples {

    public static class EmailScalar {

        public static final GraphQLScalarType EMAIL = GraphQLScalarType.newScalar()
                .name("email")
                .description("A custom scalar that handles emails")
                .coercing(new Coercing() {
                    @Override
                    public Object serialize(@NotNull Object dataFetcherResult, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
                        return serializeEmail(dataFetcherResult);
                    }

                    @Override
                    public Object parseValue(@NotNull Object input, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
                        return parseEmailFromVariable(input);
                    }

                    @Override
                    public Object parseLiteral(@NotNull Value input, @NotNull CoercedVariables variables, @NotNull GraphQLContext graphQLContext, @NotNull Locale locale) {
                        return parseEmailFromAstLiteral(input);
                    }
                })
                .build();


        private static boolean looksLikeAnEmailAddress(String possibleEmailValue) {
            // ps.  I am not trying to replicate  RFC-3696 clearly
            return Pattern.matches("[A-Za-z0-9]@[.*]", possibleEmailValue);
        }

        private static Object serializeEmail(Object dataFetcherResult) {
            String possibleEmailValue = String.valueOf(dataFetcherResult);
            if (looksLikeAnEmailAddress(possibleEmailValue)) {
                return possibleEmailValue;
            } else {
                throw new CoercingSerializeException("Unable to serialize " + possibleEmailValue + " as an email address");
            }
        }

        private static Object parseEmailFromVariable(Object input) {
            if (input instanceof String) {
                String possibleEmailValue = input.toString();
                if (looksLikeAnEmailAddress(possibleEmailValue)) {
                    return possibleEmailValue;
                }
            }
            throw new CoercingParseValueException("Unable to parse variable value " + input + " as an email address");
        }

        private static Object parseEmailFromAstLiteral(Object input) {
            if (input instanceof StringValue) {
                String possibleEmailValue = ((StringValue) input).getValue();
                if (looksLikeAnEmailAddress(possibleEmailValue)) {
                    return possibleEmailValue;
                }
            }
            throw new CoercingParseLiteralException(
                    "Value is not any email address : '" + String.valueOf(input) + "'"
            );
        }
    }

}
