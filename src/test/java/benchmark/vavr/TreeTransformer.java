package benchmark.vavr;


import graphql.PublicApi;
import graphql.util.Breadcrumb;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;

import static graphql.Assert.assertNotNull;

@PublicApi
public class TreeTransformer<T> {

    private final NodeAdapter<T> nodeAdapter;

    public TreeTransformer(NodeAdapter<T> nodeAdapter) {
        this.nodeAdapter = nodeAdapter;
    }

    public T transform(T root, TraverserVisitor<T> traverserVisitor) {
        return transform(root, traverserVisitor, LinkedHashMap.empty());
    }

    public T transform(T root, TraverserVisitor<T> traverserVisitor, Map<Class<?>, Object> rootVars) {
        assertNotNull(root);

        NodeMultiZipper<T> astMultiZipper = new NodeMultiZipper<>(root, List.empty(), nodeAdapter);

        TraverserVisitor<T> nodeTraverserVisitor = new TraverserVisitor<T>() {

            @Override
            public TraversalControl enter(TraverserContext<T> context) {
                List<Breadcrumb<T>> breadcrumbs = List.ofAll(context.getBreadcrumbs().stream());
                NodeAdapter<T> nodeAdapter = TreeTransformer.this.nodeAdapter;
                NodeZipper<T> nodeZipper = new NodeZipper<>(context.thisNode(), breadcrumbs, nodeAdapter);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, TreeTransformer.this.nodeAdapter);
                return traverserVisitor.enter(context);
            }

            @Override
            public TraversalControl leave(TraverserContext<T> context) {
                return traverserVisitor.leave(context);
            }
        };

        Traverser<T> traverser = Traverser.depthFirstWithNamedChildren(nodeAdapter::getNamedChildren, null, astMultiZipper);
        traverser.rootVars(rootVars);

        NodeMultiZipper<T> multiZipperResult = (NodeMultiZipper<T>) traverser.traverse(root, nodeTraverserVisitor).getAccumulatedResult();
        return multiZipperResult.toRootNode();
    }
}
