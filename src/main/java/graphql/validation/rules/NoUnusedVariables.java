package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

public class NoUnusedVariables extends AbstractRule{

    // TODO
    public NoUnusedVariables(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }
}
