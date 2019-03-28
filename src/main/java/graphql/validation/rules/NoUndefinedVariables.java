package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashSet;
import java.util.Set;

import static graphql.validation.ValidationErrorType.UndefinedVariable;

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
            String message = i18n(UndefinedVariable, "NoUndefinedVariables.undefinedVariable", variableReference.getName());
            addError(UndefinedVariable, variableReference.getSourceLocation(), message);
        }
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableNames.add(variableDefinition.getName());
    }
}
