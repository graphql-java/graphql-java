package graphql.validation.rules;


import graphql.ExecutionInput;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class NoUndefinedVariables extends AbstractRule {

    private final Set<String> variableNames = new LinkedHashSet<>();

    public NoUndefinedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        variableNames.clear();
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        super.checkFragmentDefinition(fragmentDefinition);
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        if (!variableNames.contains(variableReference.getName())) {
            String message = String.format("Undefined variable %s", variableReference.getName());
            addError(ValidationErrorType.UndefinedVariable, variableReference.getSourceLocation(), message);
        }
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableNames.add(variableDefinition.getName());

        getExecutionInputVariables().ifPresent(vars -> {
            if (!vars.containsKey(variableDefinition.getName())) {
                String message = String.format("Undefined variable in input %s", variableDefinition.getName());
                addError(ValidationErrorType.UndefinedVariable, variableDefinition.getSourceLocation(), message);
            }
        });
    }

    private Optional<Map<String, Object>> getExecutionInputVariables() {
        return getValidationContext().getExecutionInput().map(ExecutionInput::getVariables);
    }
}
