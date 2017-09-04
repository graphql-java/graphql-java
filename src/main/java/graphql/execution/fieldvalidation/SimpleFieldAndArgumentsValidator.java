package graphql.execution.fieldvalidation;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.ExecutionPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * This very simple field validator will run the supplied function for a given field path and if it returns an error
 * it will be added to the list of problems
 */
@PublicApi
public class SimpleFieldAndArgumentsValidator implements FieldAndArgumentsValidator {

    private Map<ExecutionPath, BiFunction<FieldAndArguments, FieldAndArgumentsValidationEnvironment, GraphQLError>> rules = new LinkedHashMap<>();

    /**
     * Adds the rule against the field address path.  If the rule returns a non null error, it will be added to to the list of errors
     *
     * @param fieldPath the path to the field
     * @param rule      the rule function
     *
     * @return this validator
     */
    public SimpleFieldAndArgumentsValidator addRule(ExecutionPath fieldPath, BiFunction<FieldAndArguments, FieldAndArgumentsValidationEnvironment, GraphQLError> rule) {
        rules.put(fieldPath, rule);
        return this;
    }

    @Override
    public List<GraphQLError> validateFieldArguments(FieldAndArgumentsValidationEnvironment validationEnvironment) {
        List<GraphQLError> errors = new ArrayList<>();
        for (ExecutionPath fieldPath : rules.keySet()) {
            FieldAndArguments fieldAndArguments = validationEnvironment.getFieldArguments().get(fieldPath);
            if (fieldAndArguments != null) {
                BiFunction<FieldAndArguments, FieldAndArgumentsValidationEnvironment, GraphQLError> ruleFunction = rules.get(fieldPath);
                GraphQLError graphQLError = ruleFunction.apply(fieldAndArguments, validationEnvironment);
                if (graphQLError != null) {
                    errors.add(graphQLError);
                }
            }
        }
        return errors;
    }
}
