package graphql.validation.rules;


import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>NoUnusedVariables class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class NoUnusedVariables extends AbstractRule {

    private List<VariableDefinition> variableDefinitions = new ArrayList<>();
    private Set<String> usedVariables = new LinkedHashSet<>();

    /**
     * <p>Constructor for NoUnusedVariables.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public NoUnusedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    /** {@inheritDoc} */
    @Override
    public void leaveOperationDefinition(OperationDefinition operationDefinition) {
        for (VariableDefinition variableDefinition : variableDefinitions) {
            if (!usedVariables.contains(variableDefinition.getName())) {
                String message = String.format("Unused variable %s",variableDefinition.getName());
                addError(new ValidationError(ValidationErrorType.UnusedVariable, variableDefinition.getSourceLocation(), message));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        usedVariables.clear();
        variableDefinitions.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableDefinitions.add(variableDefinition);
    }

    /** {@inheritDoc} */
    @Override
    public void checkVariable(VariableReference variableReference) {
        usedVariables.add(variableReference.getName());
    }
}
