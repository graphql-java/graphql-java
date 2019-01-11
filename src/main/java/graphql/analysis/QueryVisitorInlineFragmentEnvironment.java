package graphql.analysis;

import graphql.PublicApi;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorInlineFragmentEnvironment {
    InlineFragment getInlineFragment();

    TraverserContext<Node> getTraverserContext();

    /**
     * @return false if field should not be included due to evaluation of conditional directive.
     */
    boolean shouldInclude();
}
