package graphql.validation.rules;


import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class NoUndefinedVariables extends AbstractRule {

    private Set<String> variableNames = new LinkedHashSet<>();

    public NoUndefinedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        if (!variableNames.contains(variableReference.getName())) {
            addError(new ValidationError(ValidationErrorType.UndefinedVariable, variableReference.getSourceLocation(), null));
        }
    }

    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableNames.add(variableDefinition.getName());
    }
}
