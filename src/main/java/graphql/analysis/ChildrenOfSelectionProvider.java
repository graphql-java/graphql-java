package graphql.analysis;

import graphql.Internal;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.language.NodeTraverser;
import graphql.language.NodeVisitorStub;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * QueryTraversal helper class responsible for obtaining Selection
 * nodes from selection parent.
 * Uses double dispatch in order to avoid reflection based {@code instanceof} check.
 */
@Internal
public class ChildrenOfSelectionProvider extends NodeVisitorStub {

    private Map<String, FragmentDefinition> fragmentDefinitionMap;

    public ChildrenOfSelectionProvider(Map<String, FragmentDefinition> fragmentDefinitionMap) {
        this.fragmentDefinitionMap = fragmentDefinitionMap;
    }

    @Override
    public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
        getSelectionSetChildren(node.getSelectionSet(), context);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitFragmentSpread(FragmentSpread fragmentSpread, TraverserContext<Node> context) {
        getSelectionSetChildren(fragmentDefinitionMap.get(fragmentSpread.getName()).getSelectionSet(), context);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitField(Field node, TraverserContext<Node> context) {
        getSelectionSetChildren(node.getSelectionSet(), context);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitSelectionSet(SelectionSet node, TraverserContext<Node> context) {
        context.setResult(node.getSelections());
        return TraversalControl.CONTINUE;
    }

    private void getSelectionSetChildren(SelectionSet node, TraverserContext<Node> context) {
        if (node == null) {
            context.setResult(Collections.emptyList());
        } else {
            context.setResult(node.getSelections());
        }
    }

    public List<Node> getSelections(Selection selection) {
        return NodeTraverser.oneVisitWithResult(selection, this);
    }
}
