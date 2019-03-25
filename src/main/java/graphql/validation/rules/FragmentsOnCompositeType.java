package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;
import graphql.validation.ValidationErrorType;

import static graphql.validation.ValidationErrorType.FragmentTypeConditionInvalid;
import static graphql.validation.ValidationErrorType.InlineFragmentTypeConditionInvalid;

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
            String message = i18n("FragmentsOnCompositeType.invalidInlineTypeCondition", InlineFragmentTypeConditionInvalid);
            addError(InlineFragmentTypeConditionInvalid, inlineFragment.getSourceLocation(), message);
        }
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        GraphQLType type = getValidationContext().getSchema().getType(fragmentDefinition.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            String message = i18n("FragmentsOnCompositeType.invalidFragmentTypeCondition", FragmentTypeConditionInvalid);

            addError(FragmentTypeConditionInvalid, fragmentDefinition.getSourceLocation(), message);
        }
    }
}
