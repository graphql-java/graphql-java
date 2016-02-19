package graphql.validation.rules;


import graphql.ShouldNotHappenException;
import graphql.execution.TypeFromAST;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.schema.*;
import graphql.validation.*;

import java.util.Collections;
import java.util.List;

/**
 * <p>PossibleFragmentSpreads class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class PossibleFragmentSpreads extends AbstractRule {

    /**
     * <p>Constructor for PossibleFragmentSpreads.</p>
     *
     * @param validationContext a {@link graphql.validation.ValidationContext} object.
     * @param validationErrorCollector a {@link graphql.validation.ValidationErrorCollector} object.
     */
    public PossibleFragmentSpreads(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }


    /** {@inheritDoc} */
    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        GraphQLOutputType fragType = getValidationContext().getOutputType();
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (fragType == null || parentType == null) return;
        if (!doTypesOverlap(fragType, parentType)) {
            String message = String.format("Fragment cannot be spread here as objects of " +
                    "type %s can never be of type %s", parentType, fragType);
            addError(new ValidationError(ValidationErrorType.InvalidFragmentType, inlineFragment.getSourceLocation(), message));

        }
    }

    /** {@inheritDoc} */
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
        if (type == parent) {
            return true;
        }

        List<? extends GraphQLType> possibleParentTypes;
        if (parent instanceof GraphQLObjectType) {
            possibleParentTypes = Collections.<GraphQLType>singletonList(parent);
        } else if (parent instanceof GraphQLInterfaceType) {
            possibleParentTypes = new SchemaUtil().findImplementations(getValidationContext().getSchema(), (GraphQLInterfaceType) parent);
        } else if (parent instanceof GraphQLUnionType) {
            possibleParentTypes = ((GraphQLUnionType) parent).getTypes();
        } else {
            throw new ShouldNotHappenException();
        }
        List<? extends GraphQLType> possibleConditionTypes;
        if (type instanceof GraphQLObjectType) {
            possibleConditionTypes = Collections.singletonList(type);
        } else if (type instanceof GraphQLInterfaceType) {
            possibleConditionTypes = new SchemaUtil().findImplementations(getValidationContext().getSchema(), (GraphQLInterfaceType) type);
        } else if (type instanceof GraphQLUnionType) {
            possibleConditionTypes = ((GraphQLUnionType) type).getTypes();
        } else {
            throw new ShouldNotHappenException();
        }

        return !Collections.disjoint(possibleParentTypes, possibleConditionTypes);

    }
}
