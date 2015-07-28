package graphql.validation.rules;


import graphql.execution.TypeFromAST;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLType;
import graphql.validation.*;

public class PossibleFragmentSpreads extends AbstractRule {

    public PossibleFragmentSpreads(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        GraphQLInputType fragType = getValidationContext().getInputType();
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (fragType == null || parentType == null) return;
        if (!doTypesOverlap(fragType, parentType)) {
            String message = String.format("Fragment cannot be spread here as objects of " +
                    "type %s can never be of type %s", parentType, fragType);
            addError(new ValidationError(ValidationErrorType.InvalidFragmentType, inlineFragment.getSourceLocation(), message));

        }
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragment == null) return;
        GraphQLType typeCondition = TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), fragment.getTypeCondition());
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (typeCondition == null || parentType == null) return;

        if (!doTypesOverlap(typeCondition, parentType)) {
            String message = String.format("Fragment %s cannot be spread here as objects of " +
                    "type %s can never be of type %s", fragmentSpread.getName(), parentType, typeCondition);
            addError(new ValidationError(ValidationErrorType.InvalidFragmentType, fragmentSpread.getSourceLocation(), message));
        }
    }

    private boolean doTypesOverlap(GraphQLType type, GraphQLCompositeType parent) {
        return true;
    }
}
