package graphql.validation.rules;

import java.util.Map;

import graphql.language.VariableReference;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;
import graphql.validation.rules.VariablesTypesMatcher;

public class VariablesAreBoundRule extends AbstractRule {

    private final Map<String, Object> variables;
    VariablesTypesMatcher             variablesTypesMatcher = new VariablesTypesMatcher();

    public VariablesAreBoundRule(ValidationContext validationContext,
                                 ValidationErrorCollector validationErrorCollector,
                                 Map<String, Object> variables) {
        super(validationContext, validationErrorCollector);
        setVisitFragmentSpreads(true);
        this.variables = variables;
    }

    @Override
    public void checkVariable(VariableReference variableReference) {
        if (variables.containsKey(variableReference.getName())) {
            return;
        }
        String message = String.format("Variable not bound: '$%s'",
                                       variableReference.getName());
        addError(new ValidationError(ValidationErrorType.UnboundVariable,
                                     variableReference.getSourceLocation(),
                                     message));
    }

}
