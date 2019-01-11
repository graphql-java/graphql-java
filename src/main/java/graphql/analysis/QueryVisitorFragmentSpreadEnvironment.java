package graphql.analysis;

import graphql.PublicApi;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorFragmentSpreadEnvironment {
    FragmentSpread getFragmentSpread();

    FragmentDefinition getFragmentDefinition();

    TraverserContext<Node> getTraverserContext();

    /**
     * @return false if fragment should not be included due to evaluation of conditional directive.
     */
    boolean shouldInclude();
}
