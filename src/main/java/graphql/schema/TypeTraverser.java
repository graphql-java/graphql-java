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
    private static final GraphqlTypeVisitor NO_OP = new GraphqlTypeVisitorStub();

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
        return depthFirst(initTraverser(), new TraverserDelegateVisitor(graphqlTypeVisitor), roots);
    }

    public TraverserResult depthFirst(final GraphqlTypeVisitor graphqlTypeVisitor,
                                      Collection<? extends GraphQLType> roots,
                                      Map<String, GraphQLType> types) {
        return depthFirst(initTraverser().rootVar(TypeTraverser.class, types), new TraverserDelegateVisitor(graphqlTypeVisitor), roots);
    }

    public TraverserResult depthFirst(final Traverser<GraphQLType> traverser,
                                      final TraverserDelegateVisitor traverserDelegateVisitor,
                                      Collection<? extends GraphQLType> roots) {
        return doTraverse(traverser, roots, traverserDelegateVisitor);
    }

    protected Traverser<GraphQLType> initTraverser() {
        return Traverser.depthFirst(getChildren);
    }

    protected  TraverserResult doTraverse(Traverser<GraphQLType> traverser,  Collection<? extends GraphQLType> roots, TraverserDelegateVisitor traverserDelegateVisitor) {
        return traverser.traverse(roots,traverserDelegateVisitor);
    }

    class TraverserDelegateVisitor implements TraverserVisitor<GraphQLType> {
        private final GraphqlTypeVisitor before;
        private final GraphqlTypeVisitor after;


        TraverserDelegateVisitor(GraphqlTypeVisitor delegate) {
            this(delegate,NO_OP);
        }

        TraverserDelegateVisitor(GraphqlTypeVisitor delegateBefore, GraphqlTypeVisitor delegateAfter) {
            this.before = delegateBefore;
            this.after = delegateAfter;
        }


        @Override
        public TraversalControl enter(TraverserContext<GraphQLType> context) {
            return context.thisNode().accept(context, before);
        }

        @Override
        public TraversalControl leave(TraverserContext<GraphQLType> context) {
            return context.thisNode().accept(context, after);
        }
    }

}
