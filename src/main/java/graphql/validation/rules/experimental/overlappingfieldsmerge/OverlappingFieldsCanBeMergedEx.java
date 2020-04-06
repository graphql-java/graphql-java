package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLOutputType;
import graphql.validation.AbstractRule;
import graphql.validation.ValidationContext;
import graphql.validation.ValidationErrorCollector;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 * See http://facebook.github.io/graphql/June2018/#sec-Field-Selection-Merging
 */
public class OverlappingFieldsCanBeMergedEx extends AbstractRule {

    private SelectionBuilder selectionBuilder = new SelectionBuilder();

    public OverlappingFieldsCanBeMergedEx(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        super(validationContext, validationErrorCollector);
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        selectionBuilder.enterFragmentDefinition(fragmentDefinition, getValidationContext().getQueryPath());
    }

    @Override
    public void checkFragmentSpread(FragmentSpread fragmentSpread) {
        selectionBuilder.spreadFragment(fragmentSpread);
    }

    @Override
    public void checkSelectionSet(SelectionSet selectionSet) {
        selectionBuilder.enterGenericSelectionContainer(selectionSet);
    }

    @Override
    public void leaveSelectionSet(SelectionSet selectionSet) {
        selectionBuilder.leaveSelectionContainer();
    }

    @Override
    public void checkField(Field field) {
        GraphQLCompositeType parentType = getValidationContext().getParentType();
        GraphQLOutputType outputType = Optional.ofNullable(parentType)
                .filter(parent -> parent instanceof GraphQLFieldsContainer)
                .map(parent -> ((GraphQLFieldsContainer) parent).getFieldDefinition(field.getName()))
                .map(GraphQLFieldDefinition::getType)
                .orElse(null);
        selectionBuilder.enterField(parentType, field, outputType);
    }

    @Override
    public void documentFinished(Document document) {
        ArrayList<SelectionContainer> roots = selectionBuilder.build();
        CachedCheck check = new CachedCheck(this);
        roots.forEach(root -> check.checkFieldsInSetCanMerge(root.fieldSet()));
    }
}
