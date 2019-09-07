package graphql.analysis;

import graphql.PublicApi;
import graphql.language.InlineFragment;
import graphql.language.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;

@PublicApi
public interface QueryVisitorInlineFragmentEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    InlineFragment getInlineFragment();

    TraverserContext<Node> getTraverserContext();
}
