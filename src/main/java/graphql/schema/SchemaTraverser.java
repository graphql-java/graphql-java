package graphql.schema;


import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserResult;
import graphql.util.TraverserVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * This will visit all of the schema elements in the specified schema and invokes the visitor.
     *
     * @param typeVisitor a list of visitors to use
     * @param schema      the schema to visit
     *
     * @return a traversal result
     */
    public TraverserResult depthFirstFullSchema(GraphQLTypeVisitor typeVisitor, GraphQLSchema schema) {
        return depthFirstFullSchema(Collections.singletonList(typeVisitor), schema, ImmutableKit.emptyMap());
    }

    /**
     * This will visit all of the schema elements in the specified schema, invoking each visitor in turn.
     *
     * @param typeVisitors a list of visitors to use
     * @param schema       the schema to visit
     * @param rootVars     this sets up variables to be made available to the {@link TraverserContext}.  This can be empty but not null
     *
     * @return a traversal result
     */
    public TraverserResult depthFirstFullSchema(List<GraphQLTypeVisitor> typeVisitors, GraphQLSchema schema, Map<Class<?>, Object> rootVars) {
        Set<GraphQLSchemaElement> roots = new LinkedHashSet<>();
        roots.add(schema.getQueryType());
        if (schema.isSupportingMutations()) {
            roots.add(schema.getMutationType());
        }
        if (schema.isSupportingSubscriptions()) {
            roots.add(schema.getSubscriptionType());
        }
        roots.addAll(schema.getAdditionalTypes());
        roots.addAll(schema.getDirectives());
        roots.addAll(schema.getSchemaDirectives());
        roots.addAll(schema.getSchemaAppliedDirectives());
        roots.add(schema.getIntrospectionSchemaType());
        TraverserDelegateListVisitor traverserDelegateListVisitor = new TraverserDelegateListVisitor(typeVisitors);
        Traverser<GraphQLSchemaElement> traverser = initTraverser().rootVars(rootVars).rootVar(GraphQLSchema.class, schema);
        return traverser.traverse(roots, traverserDelegateListVisitor);
    }

    public TraverserResult depthFirst(GraphQLTypeVisitor graphQLTypeVisitor, GraphQLSchemaElement root) {
        return depthFirst(graphQLTypeVisitor, Collections.singletonList(root));
    }

    public TraverserResult depthFirst(final GraphQLTypeVisitor graphQLTypeVisitor, Collection<? extends GraphQLSchemaElement> roots) {
        return depthFirst(initTraverser(), new TraverserDelegateVisitor(graphQLTypeVisitor), roots);
    }


    public TraverserResult depthFirst(final Traverser<GraphQLSchemaElement> traverser,
                                      final TraverserDelegateVisitor traverserDelegateVisitor,
                                      Collection<? extends GraphQLSchemaElement> roots) {
        return doTraverse(traverser, roots, traverserDelegateVisitor);
    }

    private Traverser<GraphQLSchemaElement> initTraverser() {
        return Traverser.depthFirst(getChildren);
    }

    private TraverserResult doTraverse(Traverser<GraphQLSchemaElement> traverser,
                                       Collection<? extends GraphQLSchemaElement> roots,
                                       TraverserDelegateVisitor traverserDelegateVisitor) {
        return traverser.traverse(roots, traverserDelegateVisitor);
    }

    private static class TraverserDelegateVisitor implements TraverserVisitor<GraphQLSchemaElement> {
        private final GraphQLTypeVisitor delegate;

        TraverserDelegateVisitor(GraphQLTypeVisitor delegate) {
            this.delegate = delegate;

        }

        @Override
        public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
            return context.thisNode().accept(context, delegate);
        }

        @Override
        public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
            return CONTINUE;
        }

        @Override
        public TraversalControl backRef(TraverserContext<GraphQLSchemaElement> context) {
            return delegate.visitBackRef(context);
        }
    }

    private static class TraverserDelegateListVisitor implements TraverserVisitor<GraphQLSchemaElement> {
        private final List<GraphQLTypeVisitor> typeVisitors;

        TraverserDelegateListVisitor(List<GraphQLTypeVisitor> typeVisitors) {
            this.typeVisitors = typeVisitors;

        }

        @Override
        public TraversalControl enter(TraverserContext<GraphQLSchemaElement> context) {
            for (GraphQLTypeVisitor graphQLTypeVisitor : typeVisitors) {
                TraversalControl control = context.thisNode().accept(context, graphQLTypeVisitor);
                if (control != CONTINUE) {
                    return control;
                }
            }
            return CONTINUE;
        }

        @Override
        public TraversalControl leave(TraverserContext<GraphQLSchemaElement> context) {
            return CONTINUE;
        }

        @Override
        public TraversalControl backRef(TraverserContext<GraphQLSchemaElement> context) {
            for (GraphQLTypeVisitor graphQLTypeVisitor : typeVisitors) {
                graphQLTypeVisitor.visitBackRef(context);
            }
            return CONTINUE;
        }
    }

}
