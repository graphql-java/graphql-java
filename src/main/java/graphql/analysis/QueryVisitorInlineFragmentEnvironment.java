package graphql.analysis;

import graphql.PublicApi;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLObjectType;

import java.util.Objects;

@PublicApi
public class QueryVisitorInlineFragmentEnvironment {
    private final InlineFragment inlineFragment;
    private final GraphQLObjectType typeCondition;

    public QueryVisitorInlineFragmentEnvironment(InlineFragment inlineFragment, GraphQLObjectType typeCondition) {
        this.inlineFragment = inlineFragment;
        this.typeCondition = typeCondition;
    }

    public InlineFragment getInlineFragment() {
        return inlineFragment;
    }

    public GraphQLObjectType getTypeCondition() {
        return typeCondition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryVisitorInlineFragmentEnvironment that = (QueryVisitorInlineFragmentEnvironment) o;
        return Objects.equals(inlineFragment, that.inlineFragment) &&
                Objects.equals(typeCondition, that.typeCondition);
    }

    @Override
    public int hashCode() {

        return Objects.hash(inlineFragment, typeCondition);
    }
}
