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

    public TraverserResult depthFirst(GraphqlTypeVisitor graphqlTypeVisitor, GraphQLType root) {
        return depthFirst(graphqlTypeVisitor, Collections.singletonList(root));
    }

    public TraverserResult depthFirst(final GraphqlTypeVisitor graphqlTypeVisitor, Collection<? extends GraphQLType> roots) {
        return Traverser.depthFirst(getChildren).traverse(roots, new TraverserDelegateVisitor(graphqlTypeVisitor));
    }

    public TraverserResult depthFirst(final GraphqlTypeVisitor graphqlTypeVisitor,
                                      Collection<? extends GraphQLType> roots,
                                      Map<String, GraphQLType> types) {
        return Traverser.depthFirst(getChildren).rootVar(TypeTraverser.class, types).traverse(roots, new TraverserDelegateVisitor(graphqlTypeVisitor));
    }

    class TraverserDelegateVisitor implements TraverserVisitor<GraphQLType> {
        private final GraphqlTypeVisitor delegate;

        public TraverserDelegateVisitor(GraphqlTypeVisitor delegate) {
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
