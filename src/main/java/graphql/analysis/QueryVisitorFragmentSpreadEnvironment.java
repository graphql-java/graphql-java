package graphql.analysis;

import graphql.PublicApi;
import graphql.language.FragmentSpread;

import java.util.Objects;

@PublicApi
public class QueryVisitorFragmentSpreadEnvironment {
    private final FragmentSpread fragmentSpread;

    public QueryVisitorFragmentSpreadEnvironment(FragmentSpread fragmentSpread) {
        this.fragmentSpread = fragmentSpread;
    }

    public FragmentSpread getFragmentSpread() {
        return fragmentSpread;
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

