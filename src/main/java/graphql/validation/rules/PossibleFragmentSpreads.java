package graphql.validation.rules;


import graphql.Assert;
import graphql.Internal;
import graphql.execution.TypeFromAST;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.Collections;
import java.util.List;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.validation.ValidationErrorType.InvalidFragmentType;

@Internal
public class PossibleFragmentSpreads extends AbstractRule {

    public PossibleFragmentSpreads(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        GraphQLOutputType fragType = getValidationContext().getOutputType();
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (fragType == null || parentType == null) return;

        if (isValidTargetCompositeType(fragType) && isValidTargetCompositeType(parentType) && !doTypesOverlap(fragType, parentType)) {
            String message = i18n(InvalidFragmentType, "PossibleFragmentSpreads.inlineIncompatibleTypes", parentType.getName(), simplePrint(fragType));
            addError(InvalidFragmentType, inlineFragment.getSourceLocation(), message);
        }
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        FragmentDefinition fragment = getValidationContext().getFragment(fragmentSpread.getName());
        if (fragment == null) return;
        GraphQLType typeCondition = TypeFromAST.getTypeFromAST(getValidationContext().getSchema(), fragment.getTypeCondition());
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        if (typeCondition == null || parentType == null) return;

        if (isValidTargetCompositeType(typeCondition) && isValidTargetCompositeType(parentType) && !doTypesOverlap(typeCondition, parentType)) {
            String message = i18n(InvalidFragmentType, "PossibleFragmentSpreads.fragmentIncompatibleTypes", fragmentSpread.getName(), parentType.getName(), simplePrint(typeCondition));
            addError(InvalidFragmentType, fragmentSpread.getSourceLocation(), message);
        }
    }

    private boolean doTypesOverlap(GraphQLType type, GraphQLCompositeType parent) {
        if (type == parent) {
            return true;
        }

        List<? extends GraphQLType> possibleParentTypes = getPossibleType(parent);
        List<? extends GraphQLType> possibleConditionTypes = getPossibleType(type);

        return !Collections.disjoint(possibleParentTypes, possibleConditionTypes);

    }

    private List<? extends GraphQLType> getPossibleType(GraphQLType type) {
        List<? extends GraphQLType> possibleConditionTypes = null;
        if (type instanceof GraphQLObjectType) {
            possibleConditionTypes = Collections.singletonList(type);
        } else if (type instanceof GraphQLInterfaceType) {
            possibleConditionTypes = getValidationContext().getSchema().getImplementations((GraphQLInterfaceType) type);
        } else if (type instanceof GraphQLUnionType) {
            possibleConditionTypes = ((GraphQLUnionType) type).getTypes();
        } else {
            Assert.assertShouldNeverHappen();
        }
        return possibleConditionTypes;
    }

    /**
     * Per spec: The target type of fragment (type condition)
     * must have kind UNION, INTERFACE, or OBJECT.
     * @param type GraphQLType
     * @return true if it is a union, interface, or object.
     */
    private boolean isValidTargetCompositeType(GraphQLType type) {
        return type instanceof GraphQLCompositeType;
    }
}
