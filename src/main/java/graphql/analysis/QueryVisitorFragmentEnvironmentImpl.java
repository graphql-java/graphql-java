package graphql.analysis;

import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.language.Node;
import graphql.util.TraverserContext;

import java.util.Objects;

@Internal
public class QueryVisitorFragmentEnvironmentImpl implements QueryVisitorFragmentEnvironment {

    private final FragmentDefinition fragmentDefinition;
    private final TraverserContext<Node> traverserContext;


    public QueryVisitorFragmentEnvironmentImpl(FragmentDefinition fragmentDefinition, TraverserContext<Node> traverserContext) {
        this.fragmentDefinition = fragmentDefinition;
        this.traverserContext = traverserContext;
    }

    @Override
    public FragmentDefinition getFragmentDefinition() {
        return fragmentDefinition;
    }

    @Override
    public TraverserContext<Node> getTraverserContext() {
        return traverserContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QueryVisitorFragmentEnvironmentImpl that = (QueryVisitorFragmentEnvironmentImpl) o;
        return Objects.equals(fragmentDefinition, that.fragmentDefinition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fragmentDefinition);
    }
}

