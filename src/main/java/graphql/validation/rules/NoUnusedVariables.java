package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;

public class NoUnusedVariables extends AbstractRule{

    public NoUnusedVariables(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }
}
