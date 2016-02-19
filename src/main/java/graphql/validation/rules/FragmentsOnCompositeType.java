package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLType;
import graphql.validation.*;

/**
 * <p>FragmentsOnCompositeType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class FragmentsOnCompositeType extends AbstractRule {


    /**
     * <p>Constructor for FragmentsOnCompositeType.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public FragmentsOnCompositeType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    /** {@inheritDoc} */
    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        GraphQLType type = getValidationContext().getSchema().getType(inlineFragment.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            String message = "Inline fragment type condition is invalid, must be on Object/Interface/Union";
            addError(new ValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid, inlineFragment.getSourceLocation(), message));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        GraphQLType type = getValidationContext().getSchema().getType(fragmentDefinition.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            String message = "Fragment type condition is invalid, must be on Object/Interface/Union";
            addError(new ValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid, fragmentDefinition.getSourceLocation(), message));
        }
    }
}
