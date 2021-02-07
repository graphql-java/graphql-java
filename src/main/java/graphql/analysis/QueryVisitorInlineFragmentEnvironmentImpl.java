package graphql.analysis;

import graphql.Internal;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

import java.util.Objects;

@Internal
public class QueryVisitorInlineFragmentEnvironmentImpl implements QueryVisitorInlineFragmentEnvironment {
    private final InlineFragment inlineFragment;
    private final TraverserContext<Node> traverserContext;
    private final GraphQLSchema schema;

    public QueryVisitorInlineFragmentEnvironmentImpl(InlineFragment inlineFragment, TraverserContext<Node> traverserContext, GraphQLSchema schema) {
        this.inlineFragment = inlineFragment;
        this.traverserContext = traverserContext;
        this.schema = schema;
    }

    @Override
    public GraphQLSchema getSchema() {
        return schema;
    }

    @Override
    public InlineFragment getInlineFragment() {
        return inlineFragment;
    }

    @Override
    public TraverserContext<Node> getTraverserContext() {
        return traverserContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryVisitorInlineFragmentEnvironmentImpl that = (QueryVisitorInlineFragmentEnvironmentImpl) o;
        return Objects.equals(inlineFragment, that.inlineFragment);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inlineFragment);
    }

    @Override
    public String toString() {
        return "QueryVisitorInlineFragmentEnvironmentImpl{" +
                "inlineFragment=" + inlineFragment +
                '}';
    }
}
