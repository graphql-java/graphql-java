package graphql.validation.rules;

import com.google.common.collect.Sets;
import graphql.Internal;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.validation.ValidationErrorType.DuplicateVariableName;

/**
 * Unique variable names
 * <p>
 * A GraphQL operation is only valid if all its variables are uniquely named.
 */
@Internal
public class UniqueVariableNames extends AbstractRule {

    public UniqueVariableNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();
        if (variableDefinitions == null || variableDefinitions.size() <= 1) {
            return;
        }

        Set<String> variableNameList = Sets.newLinkedHashSetWithExpectedSize(variableDefinitions.size());


        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (variableNameList.contains(variableDefinition.getName())) {
                String message = i18n(DuplicateVariableName, "UniqueVariableNames.oneVariable", variableDefinition.getName());
                addError(DuplicateVariableName, variableDefinition.getSourceLocation(), message);
            } else {
                variableNameList.add(variableDefinition.getName());
            }
        }
    }
}
