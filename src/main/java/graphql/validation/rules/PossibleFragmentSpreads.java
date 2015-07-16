package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;

public class PossibleFragmentSpreads extends AbstractRule{

    public PossibleFragmentSpreads(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }
}
