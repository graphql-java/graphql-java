package graphql.analysis;

import graphql.Internal;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;

import java.util.Objects;

@Internal
public class QueryVisitorFragmentSpreadEnvironmentImpl implements QueryVisitorFragmentSpreadEnvironment {

    private final FragmentSpread fragmentSpread;
    private final FragmentDefinition fragmentDefinition;

    public QueryVisitorFragmentSpreadEnvironmentImpl(FragmentSpread fragmentSpread, FragmentDefinition fragmentDefinition) {
        this.fragmentSpread = fragmentSpread;
        this.fragmentDefinition = fragmentDefinition;
    }

    @Override
    public FragmentSpread getFragmentSpread() {
        return fragmentSpread;
    }

    @Override
    public FragmentDefinition getFragmentDefinition() {
        return fragmentDefinition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryVisitorFragmentSpreadEnvironmentImpl that = (QueryVisitorFragmentSpreadEnvironmentImpl) o;
        return Objects.equals(fragmentSpread, that.fragmentSpread);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fragmentSpread);
    }
}

