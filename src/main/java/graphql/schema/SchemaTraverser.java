package graphql.schema;


import graphql.PublicApi;
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

import static graphql.util.TraversalControl.CONTINUE;

@PublicApi
public class SchemaTraverser {


    private final Function<? super GraphQLSchemaElement, ? extends List<GraphQLSchemaElement>> getChildren;

    public SchemaTraverser(Function<? super GraphQLSchemaElement, ? extends List<GraphQLSchemaElement>> getChildren) {
        this.getChildren = getChildren;
    }

    public SchemaTraverser() {
        this(GraphQLSchemaElement::getChildren);
    }

    public TraverserResult depthFirst(GraphQLTypeVisitor graphQLTypeVisitor, GraphQLSchemaElement root) {
        return depthFirst(graphQLTypeVisitor, Collections.singletonList(root));
    }

    public TraverserResult depthFirst(final GraphQLTypeVisitor graphQLTypeVisitor, Collection<? extends GraphQLSchemaElement> roots) {
        return depthFirst(initTraverser(), new TraverserDelegateVisitor(graphQLTypeVisitor), roots);
    }

    public TraverserResult depthFirst(final GraphQLTypeVisitor graphQLTypeVisitor,
                                      Collection<? extends GraphQLSchemaElement> roots,
                                      Map<String, GraphQLNamedType> types) {
        Traverser<GraphQLSchemaElement> traverser = initTraverser().rootVar(SchemaTraverser.class, types);
        return depthFirst(traverser, new TraverserDelegateVisitor(graphQLTypeVisitor), roots);
    }

    public TraverserResult depthFirst(final Traverser<GraphQLSchemaElement> traverser,
                                      final TraverserDelegateVisitor traverserDelegateVisitor,
                                      Collection<? extends GraphQLSchemaElement> roots) {
        return doTraverse(traverser, roots, traverserDelegateVisitor);
    }

    private Traverser<GraphQLSchemaElement> initTraverser() {
        return Traverser.depthFirst(getChildren);
    }

    private TraverserResult doTraverse(Traverser<GraphQLSchemaElement> traverser, Collection<? extends GraphQLSchemaElement> roots, TraverserDelegateVisitor traverserDelegateVisitor) {
        return traverser.traverse(roots, traverserDelegateVisitor);
    }

    private static class TraverserDelegateVisitor implements TraverserVisitor<GraphQLSchemaElement> {
        private final GraphQLTypeVisitor before;

        TraverserDelegateVisitor(GraphQLTypeVisitor delegate) {
            this.before = delegate;

        }

        @Override
        public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
            return context.thisNode().accept(context, before);
        }

        @Override
        public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
            return CONTINUE;
        }

        @Override
        public TraversalControl backRef(TraverserContext<GraphQLSchemaElement> context) {
            return before.visitBackRef(context);
        }
    }

}
