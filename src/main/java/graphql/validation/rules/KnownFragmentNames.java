package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.UndefinedFragment;

public class KnownFragmentNames extends AbstractRule {

    public KnownFragmentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDefinition = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragmentDefinition == null) {
            String message = i18n(UndefinedFragment, "KnownFragmentNames.undefinedFragment", fragmentSpread.getName());
            addError(UndefinedFragment, fragmentSpread.getSourceLocation(), message);
        }
    }
}
