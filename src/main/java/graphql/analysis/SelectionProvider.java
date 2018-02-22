package graphql.analysis;

import graphql.Assert;
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
 * nodes from selection parent
 */
@Internal
public class SelectionProvider extends NodeVisitorStub<List<Selection>> {
    
    private final Map<String, FragmentDefinition> fragmentsByName;

    SelectionProvider(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = Assert.assertNotNull(fragmentsByName);
    }

    @Override
    public Object visit(InlineFragment node, List<Selection> data) {
        return getChildren(node.getSelectionSet());
    }

    @Override
    public Object visit(FragmentSpread fragmentSpread, List<Selection> data) {
        return getChildren(fragmentsByName.get(fragmentSpread.getName()).getSelectionSet());
    }

    @Override
    public Object visit(Field node, List<Selection> data) {
        return getChildren(node.getSelectionSet());
    }

    @Override
    public Object visit(SelectionSet node, List<Selection> data) {
        return node.getSelections();
    }

    private List<Selection> getChildren(SelectionSet node) {
        return (node == null) ? Collections.emptyList() : node.getSelections();
    } 
    
    public List<Selection> childrenOf (Selection n) {
        return (List<Selection>)n.accept(null, this);
    }
}
