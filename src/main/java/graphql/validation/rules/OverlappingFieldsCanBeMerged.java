package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

public class OverlappingFieldsCanBeMerged extends AbstractRule{

    // TODO
    public OverlappingFieldsCanBeMerged(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }
}
