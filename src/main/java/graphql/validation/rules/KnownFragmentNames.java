package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.validation.*;

/**
 * <p>KnownFragmentNames class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class KnownFragmentNames extends AbstractRule {

    /**
     * <p>Constructor for KnownFragmentNames.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public KnownFragmentNames(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDefinition = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragmentDefinition == null) {
            String message = String.format("Undefined framgent %s", fragmentSpread.getName());
            addError(new ValidationError(ValidationErrorType.UndefinedFragment, fragmentSpread.getSourceLocation(), message));
        }
    }
}
