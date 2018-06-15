package graphql.schema;


import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserResult;
import graphql.util.TraverserVisitor;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class TypeTraverser {


    private final Function<? super GraphQLType, ? extends List<GraphQLType>> getChildren;

    public TypeTraverser(Function<? super GraphQLType, ? extends List<GraphQLType>> getChildren) {
        this.getChildren = getChildren;
    }

    public TypeTraverser() {
        this(GraphQLType::getChildren);
    }

    public TraverserResult depthFirst(TypeVisitor typeVisitor, GraphQLType root) {
        return depthFirst(typeVisitor, Collections.singletonList(root));
    }

    public TraverserResult depthFirst(final TypeVisitor typeVisitor, Collection<? extends GraphQLType> roots) {
        return Traverser.depthFirst(getChildren).traverse(roots, new TraverserDelegateVisitor(typeVisitor));
    }

    public TraverserResult depthFirst(final TypeVisitor typeVisitor,
                                      Collection<? extends GraphQLType> roots,
                                      Map<String, GraphQLType> types) {
        return Traverser.depthFirst(getChildren).rootVar(TypeTraverser.class, types).traverse(roots, new TraverserDelegateVisitor(typeVisitor));
    }

    class TraverserDelegateVisitor implements TraverserVisitor<GraphQLType> {
        private final TypeVisitor delegate;

        public TraverserDelegateVisitor(TypeVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public TraversalControl enter(TraverserContext<GraphQLType> context) {
            return context.thisNode().accept(context, delegate);
        }

        @Override
        public TraversalControl leave(TraverserContext<GraphQLType> context) {
            return context.thisNode().accept(context, delegate);
        }
    }

}
