package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class NoUndefinedVariables extends AbstractRule {

    private Set<String> variableNames = new LinkedHashSet<>();

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
            addError(new ValidationError(ValidationErrorType.UndefinedVariable, variableReference.getSourceLocation(), message));
        }
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableNames.add(variableDefinition.getName());
    }
}
