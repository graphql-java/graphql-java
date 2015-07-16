package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;

public class VariablesAreInputTypes extends AbstractRule{

    public VariablesAreInputTypes(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }
}
