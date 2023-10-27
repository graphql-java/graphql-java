package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraverserContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static graphql.schema.visitor.GraphQLSchemaTraversalControl.*;

@Internal
class GraphQLSchemaVisitorEnvironmentImpl<T extends GraphQLSchemaElement> implements GraphQLSchemaVisitorEnvironment<T> {

    protected final TraverserContext<GraphQLSchemaElement> context;

    GraphQLSchemaVisitorEnvironmentImpl(TraverserContext<GraphQLSchemaElement> context) {
        this.context = context;
    }

    @Override
    public GraphQLSchema getSchema() {
        return context.getVarFromParents(GraphQLSchema.class);
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry() {
        return context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
    }

    @Override
    public T getElement() {
        //noinspection unchecked
        return (T) context.thisNode();
    }


    @Override
    public List<GraphQLSchemaElement> getLeadingElements() {
        return buildParentsImpl(schemaElement -> true);
    }

    @Override
    public List<GraphQLSchemaElement> getUnwrappedLeadingElements() {
        return buildParentsImpl(schemaElement -> !(schemaElement instanceof GraphQLModifiedType));
    }

    @NotNull
    private List<GraphQLSchemaElement> buildParentsImpl(Predicate<GraphQLSchemaElement> predicate) {
        List<GraphQLSchemaElement> list = new ArrayList<>();
        TraverserContext<GraphQLSchemaElement> parentContext = context.getParentContext();
        while (parentContext != null) {
            GraphQLSchemaElement parentNode = parentContext.thisNode();
            if (parentNode != null) {
                if (predicate.test(parentNode)) {
                    list.add(parentNode);
                }
            }
            parentContext = parentContext.getParentContext();
        }
        return list;
    }

    @Override
    public GraphQLSchemaTraversalControl ok() {
        return CONTINUE;
    }

    @Override
    public GraphQLSchemaTraversalControl quit() {
        return QUIT;
    }


    @Override
    public GraphQLSchemaTraversalControl changeNode(T schemaElement) {
        return new GraphQLSchemaTraversalControl(Control.CHANGE, schemaElement);
    }

    @Override
    public GraphQLSchemaTraversalControl deleteNode() {
        return DELETE;
    }

    @Override
    public GraphQLSchemaTraversalControl insertAfter(T toInsertAfter) {
        return new GraphQLSchemaTraversalControl(Control.INSERT_AFTER, toInsertAfter);
    }

    @Override
    public GraphQLSchemaTraversalControl insertBefore(T toInsertBefore) {
        return new GraphQLSchemaTraversalControl(Control.INSERT_BEFORE, toInsertBefore);
    }
}
