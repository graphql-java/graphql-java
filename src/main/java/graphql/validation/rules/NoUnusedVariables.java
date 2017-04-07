package graphql.validation.rules;


import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NoUnusedVariables extends AbstractRule {

    private List<VariableDefinition> variableDefinitions = new ArrayList<>();
    private Set<String> usedVariables = new LinkedHashSet<>();

    public NoUnusedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    @Override
    public void leaveOperationDefinition(OperationDefinition operationDefinition) {
        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (!usedVariables.contains(variableDefinition.getName())) {
                String message = String.format("Unused variable %s", variableDefinition.getName());
                addError(new ValidationError(ValidationErrorType.UnusedVariable, variableDefinition.getSourceLocation(), message));
            }
        }
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
