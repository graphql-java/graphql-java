package graphql.validation.rules.experimental.overlappingfieldsmerge;

import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.SelectionSet;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLOutputType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * For the validation we need another representation of the query that
 * already contains the effective selection sets for each field and
 * certain properties of the fields. As we don't want to adapt the graphql-java
 * representation, we build our own here during traversal of the query.
 *
 * Inspired by Simon Adameit's implementation in Sangria https://github.com/sangria-graphql-org/sangria/pull/12
 */
public class SelectionBuilder {

    private SelectionField.Builder fieldBuilder = new SelectionField.Builder();
    private Map<String, SelectionContainer> fragments = new HashMap<>();
    private ArrayList<SelectionContainer> roots = new ArrayList<>();
    private ArrayDeque<SelectionContainer> stack = new ArrayDeque<>();

    void enterField(GraphQLCompositeType parentType, Field astField, GraphQLOutputType outputType) {
        SelectionField field = fieldBuilder.build(astField, parentType, outputType);
        if (!stack.isEmpty()) {
            stack.peek().addField(field);
        }
        if (astField.getSelectionSet() != null) {
            stack.push(field.getChildSelection());
        }
    }

    void spreadFragment(FragmentSpread fragmentSpread) {
        if (!stack.isEmpty()) {
            stack.peek().addSpread(getOrInitFragment(fragmentSpread.getName(), null));
        }
    }

    void enterFragmentDefinition(FragmentDefinition fragmentDefinition, List<String> queryPath) {
        SelectionContainer fragment = getOrInitFragment(fragmentDefinition.getName(), fragmentDefinition.getSelectionSet());
        if (stack.isEmpty()) {
            roots.add(fragment);
        }
        stack.push(fragment);
    }

    void enterGenericSelectionContainer(SelectionSet source) {
        if (stack.isEmpty()) {
            SelectionContainer selectionContainer = new SelectionContainer(source);
            roots.add(selectionContainer);
            stack.push(selectionContainer);
        } else {
            if (stack.peek().getSourceSelectionSet() != source) {
                SelectionContainer selectionContainer = new SelectionContainer(source);
                stack.peek().addSpread(selectionContainer);
                stack.push(selectionContainer);
            }
        }
    }

    void leaveSelectionContainer() {
        stack.pop();
    }

    /**
     * @return A list of roots of the document
     */
    ArrayList<SelectionContainer> build() {
        roots.forEach(SelectionContainer::computeEffectiveSelections);
        return roots;
    }

    private SelectionContainer getOrInitFragment(String name, SelectionSet selectionSet) {
        return fragments.computeIfAbsent(name, key -> new SelectionContainer(selectionSet));
    }
}