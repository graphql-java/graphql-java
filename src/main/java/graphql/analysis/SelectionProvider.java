package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.NodeVisitorStub;
import graphql.language.Selection;
import graphql.language.SelectionSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QueryTraversal helper class responsible for obtaining Selection 
 * nodes from selection parent.
 * Uses double dispatch in order to avoid reflection based {@code instanceof} check.
 */
@Internal
public class SelectionProvider extends NodeVisitorStub<Map<String, FragmentDefinition>> {
    @Override
    public Object visitInlineFragment(InlineFragment node, Map<String, FragmentDefinition> fragmentsByName) {
        return getChildren(node.getSelectionSet());
    }

    @Override
    public Object visitFragmentSpread(FragmentSpread fragmentSpread, Map<String, FragmentDefinition> fragmentsByName) {
        return getChildren(fragmentsByName.get(fragmentSpread.getName()).getSelectionSet());
    }

    @Override
    public Object visitField(Field node, Map<String, FragmentDefinition> fragmentsByName) {
        return getChildren(node.getSelectionSet());
    }

    @Override
    public Object visitSelectionSet(SelectionSet node, Map<String, FragmentDefinition> fragmentsByName) {
        return node.getSelections();
    }

    private List<Selection> getChildren(SelectionSet node) {
        return (node == null) ? Collections.emptyList() : node.getSelections();
    } 
    
    public List<Selection> getChildren (Selection n, Map<String, FragmentDefinition> fragmentsByName) {
        return (List<Selection>)n.accept(fragmentsByName, this);
    }
}
