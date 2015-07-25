package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

public class PossibleFragmentSpreads extends AbstractRule{

    // TODO
    public PossibleFragmentSpreads(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }
}
