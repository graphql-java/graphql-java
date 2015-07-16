package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.validation.*;

public class KnownFragmentNames extends AbstractRule{

    public KnownFragmentNames(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDefinition = getValidationContext().getFragment(fragmentSpread.getName());
        if(fragmentDefinition == null){
            addError(new ValidationError(ValidationErrorType.UndefinedFragment));
        }
    }
}
