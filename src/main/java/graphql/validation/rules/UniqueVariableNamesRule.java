package graphql.validation.rules;

import graphql.language.*;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unique variable names
 * <p>
 * A GraphQL operation is only valid if all its variables are uniquely named.
 */
public class UniqueVariableNamesRule extends AbstractRule {

    public UniqueVariableNamesRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        if (variableDefinitions == null || variableDefinitions.size() <= 1) {
            return;
        }

        Set<String> variableNameList = new HashSet<>(variableDefinitions.size());


        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (variableNameList.contains(variableDefinition.getName())) {
                addError(ValidationErrorType.DuplicateVariableName, variableDefinition.getSourceLocation(), duplicateVariableNameMessage(variableDefinition.getName()));
            } else {
                variableNameList.add(variableDefinition.getName());
            }
        }
    }

    static String duplicateVariableNameMessage(String variableName) {
        return String.format("There can be only one variable named '%s'", variableName);
    }

}
