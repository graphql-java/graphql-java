package graphql.analysis;

import graphql.PublicApi;
import graphql.language.InlineFragment;

import java.util.Objects;

@PublicApi
public class QueryVisitorInlineFragmentEnvironment {
    private final InlineFragment inlineFragment;

    public QueryVisitorInlineFragmentEnvironment(InlineFragment inlineFragment) {
        this.inlineFragment = inlineFragment;
    }

    public InlineFragment getInlineFragment() {
        return inlineFragment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryVisitorInlineFragmentEnvironment that = (QueryVisitorInlineFragmentEnvironment) o;
        return Objects.equals(inlineFragment, that.inlineFragment);
    }

    @Override
    public int hashCode() {

        return Objects.hash(inlineFragment);
    }

    @Override
    public String toString() {
        return "QueryVisitorInlineFragmentEnvironment{" +
                "inlineFragment=" + inlineFragment +
                '}';
    }
}
