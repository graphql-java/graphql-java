package graphql.validation.rules;


import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;

public class ProvidedNonNullArguments extends AbstractRule{

    public ProvidedNonNullArguments(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }
}
