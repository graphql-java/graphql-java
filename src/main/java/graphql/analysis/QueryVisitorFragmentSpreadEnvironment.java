package graphql.analysis;

import graphql.PublicApi;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.Node;
import graphql.schema.GraphQLSchema;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public interface QueryVisitorFragmentSpreadEnvironment {

    /**
     * @return the graphql schema in play
     */
    GraphQLSchema getSchema();

    FragmentSpread getFragmentSpread();

    FragmentDefinition getFragmentDefinition();

    TraverserContext<Node> getTraverserContext();
}
