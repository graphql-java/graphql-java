package graphql.validation.rules;

import graphql.validation.AbstractRule;
import graphql.validation.ErrorCollector;
import graphql.validation.ValidationContext;


public class DefaultValuesOfCorrectType extends AbstractRule {


    public DefaultValuesOfCorrectType(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }


}
