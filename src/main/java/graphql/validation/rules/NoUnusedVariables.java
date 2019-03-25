package graphql.validation.rules;


import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.validation.ValidationErrorType.UnusedVariable;

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
                String message = i18n("NoUnusedVariables.unusedVariable", UnusedVariable, variableDefinition.getName());
                addError(UnusedVariable, variableDefinition.getSourceLocation(), message);
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
