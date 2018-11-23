package graphql.execution.validation;

import graphql.GraphQLError;

import java.util.function.Function;

public class Validations {

    public static ValidationResult nonNull(Object value, ValidationRuleEnvironment environment, Function<ValidationRuleEnvironment, String> errorMsg) {
        ValidationResult.Builder result = ValidationResult.newResult();
        if (value == null) {
            GraphQLError graphQLError = environment.mkError(errorMsg.apply(environment));
            result.withErrors(graphQLError);
        }
        return result.continueIfNoErrors();
    }

    public static ValidationResult max(Object value, long maxValue, ValidationRuleEnvironment environment, Function<ValidationRuleEnvironment, String> errorMsg) {
        ValidationResult.Builder result = ValidationResult.newResult();
        if (value != null) {
            Comparison<?> comparison = Comparisons.findComparison(value.getClass());
            if (comparison != null) {
                int rc = comparison.compare(value, maxValue);
                if (rc > 0) {
                    GraphQLError graphQLError = environment.mkError(errorMsg.apply(environment));
                    result.withErrors(graphQLError);
                }
            } else {
                GraphQLError graphQLError = environment.mkError(String.format("No comparison implementation is available for '%s'", value.getClass()));
                result.withErrors(graphQLError);
            }
        }
        return result.continueIfNoErrors();
    }
}
