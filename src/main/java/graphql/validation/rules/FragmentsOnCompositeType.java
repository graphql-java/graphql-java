package graphql.validation.rules;


import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import static graphql.validation.ValidationErrorType.FragmentTypeConditionInvalid;
import static graphql.validation.ValidationErrorType.InlineFragmentTypeConditionInvalid;

@Internal
public class FragmentsOnCompositeType extends AbstractRule {


    public FragmentsOnCompositeType(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        if (inlineFragment.getTypeCondition() == null) {
            return;
        }
        GraphQLType type = getValidationContext().getSchema().getType(inlineFragment.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            String message = i18n(InlineFragmentTypeConditionInvalid, "FragmentsOnCompositeType.invalidInlineTypeCondition");
            addError(InlineFragmentTypeConditionInvalid, inlineFragment.getSourceLocation(), message);
        }
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        GraphQLType type = getValidationContext().getSchema().getType(fragmentDefinition.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            String message = i18n(FragmentTypeConditionInvalid, "FragmentsOnCompositeType.invalidFragmentTypeCondition");
            addError(FragmentTypeConditionInvalid, fragmentDefinition.getSourceLocation(), message);
        }
    }
}
