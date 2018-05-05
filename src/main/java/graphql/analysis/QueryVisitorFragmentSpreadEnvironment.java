package graphql.analysis;

import graphql.PublicApi;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;

import java.util.Objects;

@PublicApi
public class QueryVisitorFragmentSpreadEnvironment {

    private final FragmentSpread fragmentSpread;
    private final FragmentDefinition fragmentDefinition;

    public QueryVisitorFragmentSpreadEnvironment(FragmentSpread fragmentSpread, FragmentDefinition fragmentDefinition) {
        this.fragmentSpread = fragmentSpread;
        this.fragmentDefinition = fragmentDefinition;
    }

    public FragmentSpread getFragmentSpread() {
        return fragmentSpread;
    }

    public FragmentDefinition getFragmentDefinition() {
        return fragmentDefinition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryVisitorFragmentSpreadEnvironment that = (QueryVisitorFragmentSpreadEnvironment) o;
        return Objects.equals(fragmentSpread, that.fragmentSpread);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fragmentSpread);
    }
}

