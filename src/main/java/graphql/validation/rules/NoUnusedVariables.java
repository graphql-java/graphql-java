package graphql.validation.rules;


import graphql.ExecutionInput;
import graphql.language.OperationDefinition;
import graphql.language.SourceLocation;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class NoUnusedVariables extends AbstractRule {

    private final List<VariableDefinition> variableDefinitions = new ArrayList<>();
    private final Set<String> usedVariables = new LinkedHashSet<>();

    public NoUnusedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    @Override
    public void leaveOperationDefinition(OperationDefinition operationDefinition) {
        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (!usedVariables.contains(variableDefinition.getName())) {
                String message = String.format("Unused variable %s", variableDefinition.getName());
                addError(ValidationErrorType.UnusedVariable, variableDefinition.getSourceLocation(), message);
            }
        }

        getExecutionInputVariables().ifPresent(vars -> {
            Set<String> declaredVariables = variableDefinitions.stream().map(VariableDefinition::getName).collect(Collectors.toSet());
            for (String inputVariable : vars.keySet()) {
                if (!declaredVariables.contains(inputVariable)) {
                    String message = String.format("Unused input variable %s", inputVariable);
                    addError(ValidationErrorType.UnusedVariable, (SourceLocation) null, message);
                }
            }
        });
    }

    private Optional<Map<String, Object>> getExecutionInputVariables() {
        return getValidationContext().getExecutionInput().map(ExecutionInput::getVariables);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        usedVariables.clear();
        variableDefinitions.clear();
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableDefinitions.add(variableDefinition);
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        usedVariables.add(variableReference.getName());
    }
}
