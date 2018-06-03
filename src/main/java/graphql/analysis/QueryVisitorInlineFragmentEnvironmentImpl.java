package graphql.analysis;

import graphql.Internal;
import graphql.language.InlineFragment;

import java.util.Objects;

@Internal
public class QueryVisitorInlineFragmentEnvironmentImpl implements QueryVisitorInlineFragmentEnvironment {
    private final InlineFragment inlineFragment;

    public QueryVisitorInlineFragmentEnvironmentImpl(InlineFragment inlineFragment) {
        this.inlineFragment = inlineFragment;
    }

    @Override
    public InlineFragment getInlineFragment() {
        return inlineFragment;
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

        return Objects.hash(inlineFragment);
    }

    @Override
    public String toString() {
        return "QueryVisitorInlineFragmentEnvironmentImpl{" +
                "inlineFragment=" + inlineFragment +
                '}';
    }
}
