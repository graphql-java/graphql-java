package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.validation.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <p>NoUndefinedVariables class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class NoUndefinedVariables extends AbstractRule {

    private Set<String> variableNames = new LinkedHashSet<>();

    /**
     * <p>Constructor for NoUndefinedVariables.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public NoUndefinedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
    }

    /** {@inheritDoc} */
    @Override
    public void checkOperationDefinition(OperationDefinition operationDefinition) {
        variableNames.clear();
    }

    /** {@inheritDoc} */
    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        super.checkFragmentDefinition(fragmentDefinition);
    }

    /** {@inheritDoc} */
    @Override
    public void checkVariable(VariableReference variableReference) {
        if (!variableNames.contains(variableReference.getName())) {
            String message = String.format("Undefined variable %s",variableReference.getName());
            addError(new ValidationError(ValidationErrorType.UndefinedVariable, variableReference.getSourceLocation(), message));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void checkVariableDefinition(VariableDefinition variableDefinition) {
        variableNames.add(variableDefinition.getName());
    }
}
