package graphql.execution.instrumentation.fieldvalidation;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.ExecutionPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * This very simple field validation will run the supplied function for a given field path and if it returns an error
 * it will be added to the list of problems.
 *
 * Use {@link #addRule(graphql.execution.ExecutionPath, java.util.function.BiFunction)} to supply the rule callbacks where
 * you implement your specific business logic
 */
@PublicApi
public class SimpleFieldValidation implements FieldValidation {

    private Map<ExecutionPath, BiFunction<FieldAndArguments, FieldValidationEnvironment, GraphQLError>> rules = new LinkedHashMap<>();

    /**
     * Adds the rule against the field address path.  If the rule returns a non null error, it will be added to the list of errors
     *
     * @param fieldPath the path to the field
     * @param rule      the rule function
     *
     * @return this validator
     */
    public SimpleFieldValidation addRule(ExecutionPath fieldPath, BiFunction<FieldAndArguments, FieldValidationEnvironment, GraphQLError> rule) {
        rules.put(fieldPath, rule);
        return this;
    }

    @Override
    public List<GraphQLError> validateField(FieldValidationEnvironment validationEnvironment) {
        List<GraphQLError> errors = new ArrayList<>();
        for (ExecutionPath fieldPath : rules.keySet()) {
            FieldAndArguments fieldAndArguments = validationEnvironment.getFields().get(fieldPath);
            if (fieldAndArguments != null) {
                BiFunction<FieldAndArguments, FieldValidationEnvironment, GraphQLError> ruleFunction = rules.get(fieldPath);
                GraphQLError graphQLError = ruleFunction.apply(fieldAndArguments, validationEnvironment);
                if (graphQLError != null) {
                    errors.add(graphQLError);
                }
            }
        }
        return errors;
    }
}
